package com.shai.capitall.util.pdf

import com.shai.capitall.util.csv.BankStatementParser
import com.shai.capitall.util.csv.ImportError
import com.shai.capitall.util.csv.ImportException
import com.shai.capitall.util.csv.ImportRow
import com.shai.capitall.util.csv.ParsedStatement
import com.shai.capitall.util.csv.StatementKind
import kotlin.math.abs

/**
 * מקטע טקסט בודד מתוך PDF, יחד עם מיקומו בעמוד.
 *
 * זהו בדיוק מה שספריית ה-PDF מספקת (טקסט + תיבה תוחמת), והפרדתו למודל נתונים פשוט
 * היא מה שמאפשר לבדוק את כל לוגיקת הפענוח בלי PDF אמיתי ובלי Android.
 */
data class PdfTextSpan(
    val text: String,
    val x0: Float,
    val x1: Float,
    val y: Float
) {
    val centerX: Float get() = (x0 + x1) / 2f
}

/**
 * מפענח דף חשבון בנק מ-PDF לרשימת [ImportRow].
 *
 * PDF אינו שומר טבלאות אלא "מחרוזת במיקום", ולכן הטבלה משוחזרת מהגאומטריה:
 * קיבוץ לשורות לפי הקואורדינטה האנכית, וזיהוי עמודות לפי מרכזי הכותרות. השיוך של
 * כל סכום לעמודה נעשה לפי הכותרת שמרכזה הקרוב ביותר — כך זה עמיד לרוחב משתנה של
 * המספרים (‎1,258.52 רחב מ-‎0.44) ולא תלוי בטווחים קשיחים.
 *
 * הטקסט העברי נשמר כפי שהספרייה מחזירה אותו. בדיקה על דף חשבון אמיתי הראתה שהמילים
 * עצמן יוצאות בכתיב תקין ורציף (רק סדר הסגמנטים עשוי להתהפך), ולכן התאמת מילות
 * המפתח של [com.shai.capitall.util.csv.MerchantCategorizer] עובדת עליו כמו שהיא.
 *
 * לוגיקה טהורה (ללא Android) — ראה PdfStatementParserTest.
 */
object PdfStatementParser {

    /** הפרש אנכי מרבי שעדיין נחשב לאותה שורה. שורות הטבלה מרוחקות ~23pt זו מזו. */
    private const val ROW_TOLERANCE = 3f

    /** מרווח אופקי מרבי לאיחוד שני מקטעים לכותרת אחת ("י" + "תרה" + "מצטברת"). */
    private const val HEADER_MERGE_GAP = 5f

    private val DATE_PATTERN = Regex("""\d{1,2}[./\-]\d{1,2}[./\-]\d{2,4}""")

    private data class Column(val kind: BankStatementParser.HeaderKind, val centerX: Float)

    fun parse(spans: List<PdfTextSpan>): Result<ParsedStatement> {
        val meaningful = spans.filter { it.text.isNotBlank() }
        if (meaningful.isEmpty()) return Result.failure(ImportException(ImportError.EMPTY_FILE))

        val rows = groupIntoRows(meaningful)
        val header = findHeaderRow(rows) ?: return Result.failure(ImportException(ImportError.NO_HEADER))
        val columns = header.columns

        val dateColumn = columns.firstOrNull { it.kind == BankStatementParser.HeaderKind.DATE }
            ?: return Result.failure(ImportException(ImportError.NO_HEADER))
        val moneyColumns = columns.filter {
            it.kind == BankStatementParser.HeaderKind.DEBIT ||
                it.kind == BankStatementParser.HeaderKind.CREDIT ||
                it.kind == BankStatementParser.HeaderKind.AMOUNT ||
                it.kind == BankStatementParser.HeaderKind.BALANCE
        }
        if (moneyColumns.none { it.kind != BankStatementParser.HeaderKind.BALANCE }) {
            return Result.failure(ImportException(ImportError.NO_HEADER))
        }

        // הגבול בין אזור הטקסט (תאריך + תיאור) לאזור הסכומים: אמצע הדרך בין עמודת
        // התיאור לעמודה הכספית הימנית ביותר. מתחת לגבול — מספרים, מעליו — טקסט.
        val textZoneLeftEdge = textZoneBoundary(columns, dateColumn)

        val hasDebitCredit = columns.any {
            it.kind == BankStatementParser.HeaderKind.DEBIT ||
                it.kind == BankStatementParser.HeaderKind.CREDIT
        }

        var skipped = 0
        val parsedRows = mutableListOf<ImportRow>()

        for (row in rows) {
            if (row.y <= header.y + ROW_TOLERANCE) continue // הכותרת ומה שמעליה (פרטי חשבון)

            val textSpans = row.spans.filter { it.centerX >= textZoneLeftEdge }
                .sortedByDescending { it.x0 } // עברית נקראת מימין לשמאל
            val text = textSpans.joinToString(" ") { it.text.trim() }

            val dateMatch = DATE_PATTERN.find(text)
            val timestamp = dateMatch?.value?.let { BankStatementParser.parseDate(it) }
            if (timestamp == null) {
                skipped++ // כותרת חוזרת, שורת סיכום, הערת שוליים
                continue
            }
            val merchant = text.removeRange(dateMatch.range).trim().trim('-', '|', ',').trim()

            val amount = extractAmount(row.spans, textZoneLeftEdge, moneyColumns, hasDebitCredit)
            if (amount == null || amount == 0.0) {
                skipped++
                continue
            }

            parsedRows.add(ImportRow(timestamp, merchant, amount))
        }

        if (parsedRows.isEmpty()) return Result.failure(ImportException(ImportError.NO_ROWS))

        // אותה הבחנה כמו ב-CSV: עמודת סכום יחידה שכולה חיובית היא פירוט חיובי אשראי
        val kind = if (!hasDebitCredit && parsedRows.all { it.amount > 0 }) {
            StatementKind.CREDIT_CARD
        } else {
            StatementKind.BANK_ACCOUNT
        }
        val normalized = if (kind == StatementKind.CREDIT_CARD) {
            parsedRows.map { it.copy(amount = -it.amount) }
        } else {
            parsedRows
        }

        return Result.success(ParsedStatement(normalized, kind, skipped))
    }

    // ---------- שורות ----------

    private data class Row(val y: Float, val spans: List<PdfTextSpan>)

    private data class Header(val y: Float, val columns: List<Column>)

    /** מקבץ מקטעים לשורות לפי קרבה אנכית, וממיין את השורות מלמעלה למטה. */
    private fun groupIntoRows(spans: List<PdfTextSpan>): List<Row> {
        val sorted = spans.sortedBy { it.y }
        val rows = mutableListOf<MutableList<PdfTextSpan>>()
        for (span in sorted) {
            val current = rows.lastOrNull()
            if (current != null && abs(span.y - current.first().y) <= ROW_TOLERANCE) {
                current.add(span)
            } else {
                rows.add(mutableListOf(span))
            }
        }
        return rows.map { group -> Row(group.first().y, group) }
    }

    /**
     * מאתר את שורת הכותרת — זו שממנה מזוהים הכי הרבה סוגי עמודות. נדרשים לפחות
     * תאריך ועמודה כספית אחת, כדי ששורת נתונים אקראית לא תיבחר בטעות ככותרת.
     */
    private fun findHeaderRow(rows: List<Row>): Header? {
        var best: Header? = null
        var bestScore = 0

        for (row in rows) {
            val columns = mergeAdjacent(row.spans).mapNotNull { merged ->
                BankStatementParser.classifyHeader(merged.text)?.let { Column(it, merged.centerX) }
            }.distinctBy { it.kind }

            val hasDate = columns.any { it.kind == BankStatementParser.HeaderKind.DATE }
            val hasMoney = columns.any {
                it.kind == BankStatementParser.HeaderKind.DEBIT ||
                    it.kind == BankStatementParser.HeaderKind.CREDIT ||
                    it.kind == BankStatementParser.HeaderKind.AMOUNT
            }
            if (!hasDate || !hasMoney) continue

            if (columns.size > bestScore) {
                bestScore = columns.size
                best = Header(row.y, columns)
            }
        }
        return best
    }

    /**
     * מאחד מקטעים סמוכים אופקית למחרוזת אחת. נדרש כי מנוע ה-PDF מפצל לעיתים כותרת
     * אחת לכמה מקטעים ("יתרה מצטברת" הגיעה כ-"י" + "תרה" + "מצטברת").
     */
    private fun mergeAdjacent(spans: List<PdfTextSpan>): List<PdfTextSpan> {
        if (spans.isEmpty()) return emptyList()
        val ordered = spans.sortedByDescending { it.x0 } // ימין לשמאל
        val merged = mutableListOf<PdfTextSpan>()
        var current = ordered.first()

        for (next in ordered.drop(1)) {
            // "סמוך" = הקצה השמאלי של הנוכחי כמעט נוגע בקצה הימני של הבא
            if (current.x0 - next.x1 <= HEADER_MERGE_GAP) {
                current = PdfTextSpan(
                    text = current.text + next.text,
                    x0 = next.x0,
                    x1 = current.x1,
                    y = current.y
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    // ---------- עמודות וסכומים ----------

    private fun textZoneBoundary(columns: List<Column>, dateColumn: Column): Float {
        val rightmostMoney = columns
            .filter { it.kind != BankStatementParser.HeaderKind.DATE && it.kind != BankStatementParser.HeaderKind.MERCHANT }
            .maxByOrNull { it.centerX }
        val leftmostText = columns
            .filter { it.kind == BankStatementParser.HeaderKind.DATE || it.kind == BankStatementParser.HeaderKind.MERCHANT }
            .minByOrNull { it.centerX } ?: dateColumn

        return if (rightmostMoney != null) {
            (leftmostText.centerX + rightmostMoney.centerX) / 2f
        } else {
            leftmostText.centerX
        }
    }

    /**
     * מוצא את סכום העסקה בשורה: כל מקטע מספרי משויך לעמודה הכספית שמרכזה הקרוב
     * ביותר. חובה הופך לשלילי, זכות לחיובי, ועמודת היתרה המצטברת נזרקת.
     */
    private fun extractAmount(
        spans: List<PdfTextSpan>,
        textZoneLeftEdge: Float,
        moneyColumns: List<Column>,
        hasDebitCredit: Boolean
    ): Double? {
        var debit = 0.0
        var credit = 0.0
        var plain: Double? = null

        for (span in spans) {
            if (span.centerX >= textZoneLeftEdge) continue
            val value = BankStatementParser.parseAmount(span.text) ?: continue // ₪ ותאים ריקים
            val column = moneyColumns.minByOrNull { abs(it.centerX - span.centerX) } ?: continue

            when (column.kind) {
                BankStatementParser.HeaderKind.DEBIT -> debit = abs(value)
                BankStatementParser.HeaderKind.CREDIT -> credit = abs(value)
                BankStatementParser.HeaderKind.AMOUNT -> plain = value
                else -> Unit // BALANCE — יתרה מצטברת, לא סכום עסקה
            }
        }

        return when {
            hasDebitCredit && (debit != 0.0 || credit != 0.0) -> credit - debit
            hasDebitCredit -> null
            else -> plain
        }
    }
}
