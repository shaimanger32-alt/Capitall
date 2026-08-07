package com.shai.capitall.util.csv

import java.nio.charset.Charset

/**
 * קורא CSV טהור (ללא Android) בסגנון RFC-4180: תומך בשדות במרכאות, במפריד בתוך מרכאות,
 * במרכאות כפולות כתו בריחה ובסופי שורה CRLF/LF.
 *
 * יצוא מבנקים ומחברות אשראי בישראל מגיע בשלושה מפרידים שונים (פסיק / נקודה-פסיק / טאב)
 * ובשתי קידודים (UTF-8 ו-windows-1255), ולכן שניהם מזוהים אוטומטית ולא מונחים מראש.
 */
/** סוג הקובץ שזוהה לפי חתימת הבתים הראשונים שלו (magic number). */
enum class FileSignature { TEXT, SPREADSHEET, PDF }

object CsvReader {

    /** קידוד עברי של Windows — הקידוד הנפוץ ביצוא מהבנקים הישראליים. */
    private const val HEBREW_ANSI = "windows-1255"

    private val DELIMITERS = charArrayOf(',', ';', '\t')

    /**
     * מזהה קבצים בינאריים נפוצים לפני שמנסים לפענח אותם כטקסט.
     *
     * רוב הבנקים בישראל מציעים "יצוא לאקסל" ולא CSV, ולכן זו הטעות הצפויה ביותר של
     * המשתמש. בלי הזיהוי הזה קובץ xlsx היה מפוענח כג'יבריש ומקבל הודעת "לא נמצאו
     * עמודות תאריך וסכום" — הודעה נכונה טכנית אבל חסרת תועלת.
     */
    fun detectSignature(bytes: ByteArray): FileSignature {
        fun startsWith(vararg magic: Int): Boolean =
            bytes.size >= magic.size && magic.withIndex().all { (i, b) -> bytes[i] == b.toByte() }

        return when {
            // xlsx/xlsm/ods הם ארכיוני ZIP
            startsWith(0x50, 0x4B, 0x03, 0x04) -> FileSignature.SPREADSHEET
            // xls ישן — מסמך OLE2 מורכב
            startsWith(0xD0, 0xCF, 0x11, 0xE0) -> FileSignature.SPREADSHEET
            startsWith(0x25, 0x50, 0x44, 0x46) -> FileSignature.PDF // "%PDF"
            else -> FileSignature.TEXT
        }
    }

    /**
     * ממיר את בתי הקובץ לטקסט. סדר הזיהוי: BOM מפורש → UTF-8 תקין → windows-1255.
     *
     * חשוב: פענוח UTF-8 "רגיל" לעולם לא נכשל (בתים לא חוקיים הופכים ל-U+FFFD), ולכן
     * הבדיקה נעשית עם CharsetDecoder במצב REPORT — שם בתים לא חוקיים כן זורקים חריגה,
     * וזה מה שמאפשר להבחין בין קובץ UTF-8 לקובץ עברי בקידוד ANSI.
     */
    fun decode(bytes: ByteArray): String {
        // BOM של UTF-8 — סימן חד-משמעי, וגם צריך להיחתך כדי שלא יזהם את הכותרת הראשונה
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }

        val strictUtf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        if (strictUtf8 != null) return strictUtf8

        val hebrew = runCatching { Charset.forName(HEBREW_ANSI) }.getOrNull()
        return if (hebrew != null) String(bytes, hebrew) else String(bytes, Charsets.UTF_8)
    }

    /**
     * בוחר את המפריד שמייצר את מספר העמודות העקבי והגדול ביותר בשורות הראשונות.
     * (ספירה מחוץ למרכאות בלבד — שם עסק כמו "כהן, בע\"מ" לא ייחשב כמפריד.)
     */
    fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(20).toList()
        if (sample.isEmpty()) return ','

        return DELIMITERS.maxByOrNull { delimiter ->
            val counts = sample.map { line -> countOutsideQuotes(line, delimiter) }.filter { it > 0 }
            if (counts.isEmpty()) return@maxByOrNull 0
            // מפריד טוב = מופיע בהרבה שורות, ובאותו מספר מופעים בכל שורה
            val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value }
            (mode?.value ?: 0) * (mode?.key ?: 0)
        } ?: ','
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> count++
            }
        }
        return count
    }

    /** מפרק את הטקסט לשורות של שדות. שורות ריקות לגמרי מושמטות. */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString().trim())
            field = StringBuilder()
        }

        fun endRow() {
            endField()
            if (row.any { it.isNotEmpty() }) rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++ // "" בתוך מרכאות = מרכאה בודדת
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> endField()
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    // \r\n נספר כסוף שורה אחד
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows
    }
}
