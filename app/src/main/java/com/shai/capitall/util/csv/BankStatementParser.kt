package com.shai.capitall.util.csv

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** שורה שנקראה בהצלחה מקובץ הבנק. הסכום חתום: שלילי = הוצאה, חיובי = הכנסה. */
data class ImportRow(
    val timestamp: Long,
    val merchant: String,
    val amount: Double
)

/** סוג הקובץ שזוהה — קובע את אמצעי התשלום ואת ברירת המחדל לסימן הסכום. */
enum class StatementKind {
    /** דף חשבון בנק: עמודות חובה/זכות נפרדות, או עמודת סכום עם ערכים חתומים. */
    BANK_ACCOUNT,

    /** פירוט כרטיס אשראי: עמודת סכום אחת שכולה חיובים (ערכים חיוביים = הוצאות). */
    CREDIT_CARD
}

enum class ImportError {
    /** לא נמצאה שורת כותרת עם עמודות מזוהות (תאריך + סכום). */
    NO_HEADER,

    /** נמצאה כותרת אך אף שורת נתונים לא נקראה בהצלחה. */
    NO_ROWS,

    /** הקובץ ריק או לא מכיל טקסט. */
    EMPTY_FILE,

    /** נבחר קובץ אקסל (xls/xlsx) ולא CSV — הטעות הנפוצה ביותר, כי כך מייצאים רוב הבנקים. */
    SPREADSHEET_FILE,

    /** ה-PDF מוגן בסיסמה (בנקים בישראל נוהגים להצפין לפי ת"ז). */
    PDF_LOCKED,

    /** ה-PDF פגום או שאינו מכיל שכבת טקסט (למשל דף סרוק). */
    PDF_UNREADABLE
}

data class ParsedStatement(
    val rows: List<ImportRow>,
    val kind: StatementKind,
    /** שורות שנמצאו אחרי הכותרת אך לא ניתן היה לפענח (סיכומים, שורות ריקות, תאריך פגום). */
    val skippedRows: Int
)

/**
 * מפענח קובצי CSV של בנקים וחברות אשראי בישראל לרשימת [ImportRow].
 *
 * לוגיקה טהורה (ללא Android/Firebase) כדי שתהיה ניתנת לבדיקה — ראה BankStatementParserTest.
 *
 * הפורמטים בישראל אינם אחידים: לכל בנק כותרות משלו, לעיתים יש שורות כותרת/סיכום מעל
 * הטבלה, והסכום מופיע או כעמודה חתומה אחת או כזוג עמודות חובה/זכות. לכן הזיהוי נעשה
 * לפי *שמות* העמודות (טבלת מילים נרדפות בעברית ובאנגלית) ולא לפי מיקום קבוע.
 */
object BankStatementParser {

    // ---------- מילים נרדפות לכותרות ----------

    private val DATE_HEADERS = listOf(
        "תאריך עסקה", "תאריך העסקה", "תאריך חיוב", "תאריך ערך", "תאריך רכישה", "תאריך",
        "date", "transaction date", "value date", "posting date"
    )

    private val MERCHANT_HEADERS = listOf(
        "שם בית עסק", "בית עסק", "תיאור העסקה", "תיאור", "פירוט", "פרטים",
        "סוג תנועה", "סוג עסקה", "תנועה",
        "description", "merchant", "details", "narrative", "payee", "name"
    )

    private val DEBIT_HEADERS = listOf(
        "חובה", "בחובה", "סכום חובה", "משיכה", "debit", "withdrawal", "charge"
    )

    private val CREDIT_HEADERS = listOf(
        "זכות", "בזכות", "סכום זכות", "הפקדה", "credit", "deposit"
    )

    // "תנועה" לבדה אינה כאן בכוונה — היא חלק מ"סוג תנועה" (עמודת התיאור) ולא מעמודת סכום
    private val AMOUNT_HEADERS = listOf(
        "סכום חיוב", "סכום העסקה", "סכום בשח", "סכום ב שח", "סכום",
        "amount", "sum", "transaction amount", "charge amount"
    )

    /** עמודות שאסור לבלבל עם עמודת הסכום — יתרת החשבון היא מצטברת, לא סכום עסקה. */
    private val BALANCE_HEADERS = listOf("יתרה", "יתרת חשבון", "balance", "running balance")

    private val DATE_FORMATS = listOf(
        "dd/MM/yyyy", "dd/MM/yy", "dd-MM-yyyy", "dd-MM-yy",
        "dd.MM.yyyy", "dd.MM.yy", "yyyy-MM-dd", "yyyy/MM/dd"
    )

    /** מספר השורות הראשונות שבהן מחפשים את שורת הכותרת (יש בנקים ששמים כותרות מעל הטבלה). */
    private const val HEADER_SEARCH_DEPTH = 25

    // ---------- נירמול ----------

    /**
     * מנרמל טקסט להשוואה: מסיר סימני כיווניות (RTL/LTR marks) שמגיעים בקבצים עבריים,
     * גרשיים, סימני פיסוק ורווחים כפולים. בלי זה "סכום חיוב" ו-"סכום־חיוב" לא יזוהו כזהים.
     */
    internal fun normalize(raw: String): String = raw
        .replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\ufeff]"), "")
        .replace(Regex("[\"'`׳״]"), "")
        .replace(Regex("[.,\\-_/\\\\()\\[\\]]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase(Locale.ROOT)

    /** סוג העמודה שזוהה מתוך טקסט כותרת בודד. */
    internal enum class HeaderKind { DATE, MERCHANT, DEBIT, CREDIT, AMOUNT, BALANCE }

    /**
     * מסווג טקסט כותרת בודד לסוג עמודה. משמש את פרסר ה-PDF, שבו הכותרות מגיעות
     * כתאים נפרדים עם מיקום ולא כשורה אחת — כך שתי הצורות עובדות מאותה טבלת
     * מילים נרדפות, ותוספת של שם עמודה חדש מועילה לשניהם בבת אחת.
     *
     * התאמה מדויקת נבדקת לפני התאמה חלקית בכל הסוגים, כדי ש"סוג תנועה" ייקלט
     * כתיאור ולא ייחטף על ידי התאמה חלקית של עמודה אחרת.
     */
    internal fun classifyHeader(raw: String): HeaderKind? {
        val text = normalize(raw)
        if (text.isBlank()) return null

        val ordered = listOf(
            HeaderKind.BALANCE to BALANCE_HEADERS,   // לפני AMOUNT — "יתרה" אינה סכום עסקה
            HeaderKind.DATE to DATE_HEADERS,
            HeaderKind.DEBIT to DEBIT_HEADERS,
            HeaderKind.CREDIT to CREDIT_HEADERS,
            HeaderKind.MERCHANT to MERCHANT_HEADERS,
            HeaderKind.AMOUNT to AMOUNT_HEADERS
        )
        ordered.forEach { (kind, candidates) ->
            if (candidates.any { text == normalize(it) }) return kind
        }
        ordered.forEach { (kind, candidates) ->
            if (candidates.any { text.contains(normalize(it)) }) return kind
        }
        return null
    }

    private fun matchColumn(headers: List<String>, candidates: List<String>): Int? {
        val normalized = headers.map { normalize(it) }
        // התאמה מדויקת קודמת להתאמה חלקית, כדי ש"סכום" לא יחטוף עמודה בשם "סכום יתרה"
        candidates.forEach { candidate ->
            val target = normalize(candidate)
            normalized.indexOfFirst { it == target }.takeIf { it >= 0 }?.let { return it }
        }
        candidates.forEach { candidate ->
            val target = normalize(candidate)
            normalized.indexOfFirst { it.contains(target) }.takeIf { it >= 0 }?.let { return it }
        }
        return null
    }

    // ---------- ערכים ----------

    /**
     * מפענח סכום כספי. מטפל בפסיקי אלפים, בסימן ₪/שח, בסוגריים כשליליים, במינוס נגרר,
     * ובסימני כיווניות שמגיעים בקבצים עבריים.
     */
    internal fun parseAmount(raw: String): Double? {
        var text = raw
            .replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\ufeff]"), "")
            .replace(Regex("[₪$€]|שח|ש\"ח|ils|nis", RegexOption.IGNORE_CASE), "")
            .replace(",", "")
            .replace(" ", "")
            .trim()
        if (text.isEmpty()) return null

        var negative = false
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true
            text = text.substring(1, text.length - 1).trim()
        }
        if (text.endsWith("-")) { // מינוס נגרר, נפוץ ביצוא עברי
            negative = true
            text = text.dropLast(1).trim()
        }
        if (text.startsWith("-")) {
            negative = true
            text = text.drop(1).trim()
        }
        if (text.startsWith("+")) text = text.drop(1).trim()

        val value = text.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }

    /** מפענח תאריך לפי רשימת הפורמטים הנתמכים. מחזיר null אם אף פורמט לא מתאים. */
    internal fun parseDate(raw: String): Long? {
        val text = raw
            .replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\ufeff]"), "")
            .trim()
            .substringBefore(' ') // יש קבצים עם "12/03/2025 14:33"
        if (text.isEmpty()) return null

        for (pattern in DATE_FORMATS) {
            val parsed = runCatching {
                val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                format.parse(text)
            }.getOrNull()
            if (parsed != null) {
                // מקבעים לצהריים כדי שהמרות אזור-זמן לא יזיזו את העסקה ליום הקודם
                return Calendar.getInstance().apply {
                    time = parsed
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }
        return null
    }

    // ---------- פענוח ----------

    fun parse(text: String): Result<ParsedStatement> {
        val table = CsvReader.parse(text)
        if (table.isEmpty()) return Result.failure(ImportException(ImportError.EMPTY_FILE))

        val headerIndex = findHeaderRow(table) ?: return Result.failure(ImportException(ImportError.NO_HEADER))
        val headers = table[headerIndex]

        val dateCol = matchColumn(headers, DATE_HEADERS)
            ?: return Result.failure(ImportException(ImportError.NO_HEADER))
        val merchantCol = matchColumn(headers, MERCHANT_HEADERS)
        val debitCol = matchColumn(headers, DEBIT_HEADERS)
        val creditCol = matchColumn(headers, CREDIT_HEADERS)
        val balanceCol = matchColumn(headers, BALANCE_HEADERS)
        val amountCol = matchColumn(headers, AMOUNT_HEADERS)?.takeIf { it != balanceCol }

        if (debitCol == null && creditCol == null && amountCol == null) {
            return Result.failure(ImportException(ImportError.NO_HEADER))
        }

        val hasDebitCredit = debitCol != null || creditCol != null
        var skipped = 0
        val rows = mutableListOf<ImportRow>()

        for (rowIndex in (headerIndex + 1) until table.size) {
            val cells = table[rowIndex]
            val timestamp = cells.getOrNull(dateCol)?.let { parseDate(it) }
            if (timestamp == null) {
                skipped++ // שורת סיכום / שורה ריקה / תאריך פגום
                continue
            }

            val amount = if (hasDebitCredit) {
                // חובה = יציאת כסף (שלילי), זכות = כניסת כסף (חיובי)
                val debit = debitCol?.let { cells.getOrNull(it)?.let(::parseAmount) } ?: 0.0
                val credit = creditCol?.let { cells.getOrNull(it)?.let(::parseAmount) } ?: 0.0
                if (debit == 0.0 && credit == 0.0) null else credit - kotlin.math.abs(debit)
            } else {
                cells.getOrNull(amountCol!!)?.let(::parseAmount)
            }
            if (amount == null || amount == 0.0) {
                skipped++
                continue
            }

            val merchant = merchantCol?.let { cells.getOrNull(it) }
                ?.replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\ufeff]"), "")
                ?.trim()
                .orEmpty()

            rows.add(ImportRow(timestamp, merchant, amount))
        }

        if (rows.isEmpty()) return Result.failure(ImportException(ImportError.NO_ROWS))

        // פירוט אשראי מזוהה לפי עמודת סכום יחידה שכל ערכיה חיוביים — שם "סכום" הוא גובה
        // החיוב ולא כיוון התנועה, ולכן כל השורות הן הוצאות.
        val kind = if (!hasDebitCredit && rows.all { it.amount > 0 }) {
            StatementKind.CREDIT_CARD
        } else {
            StatementKind.BANK_ACCOUNT
        }
        val normalizedRows = if (kind == StatementKind.CREDIT_CARD) {
            rows.map { it.copy(amount = -it.amount) }
        } else {
            rows
        }

        return Result.success(ParsedStatement(normalizedRows, kind, skipped))
    }

    /** בוחר את השורה בעלת הסימנים הרבים ביותר לכותרת (תאריך + סכום/חובה/זכות). */
    private fun findHeaderRow(table: List<List<String>>): Int? {
        var best: Int? = null
        var bestScore = 0
        for (index in 0 until minOf(HEADER_SEARCH_DEPTH, table.size)) {
            val row = table[index]
            if (row.size < 2) continue
            var score = 0
            if (matchColumn(row, DATE_HEADERS) != null) score += 2
            if (matchColumn(row, AMOUNT_HEADERS) != null) score++
            if (matchColumn(row, DEBIT_HEADERS) != null) score++
            if (matchColumn(row, CREDIT_HEADERS) != null) score++
            if (matchColumn(row, MERCHANT_HEADERS) != null) score++
            if (score > bestScore) {
                bestScore = score
                best = index
            }
        }
        // דורשים לפחות תאריך + עמודה כספית אחת כדי לא לקבוע כותרת על שורת נתונים אקראית
        return if (bestScore >= 3) best else null
    }
}

/** נשלח בתוך [Result.failure] כדי לשמר את סיבת הכשל הממופה למחרוזת ב-UI. */
class ImportException(val error: ImportError) : Exception(error.name)
