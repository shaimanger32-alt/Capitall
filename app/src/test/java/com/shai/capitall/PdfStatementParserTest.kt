package com.shai.capitall

import com.shai.capitall.util.csv.ImportError
import com.shai.capitall.util.csv.ImportException
import com.shai.capitall.util.csv.StatementKind
import com.shai.capitall.util.pdf.PdfStatementParser
import com.shai.capitall.util.pdf.PdfTextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * הגאומטריה כאן משחזרת דף חשבון אמיתי של בנק לאומי (מיקומי עמודות, מרווח שורות של
 * 23.2pt, ופיצול הכותרת "יתרה מצטברת" לשלושה מקטעים). הערכים עצמם — שמות בתי עסק,
 * סכומים ומספרי חשבון — מומצאים.
 */
class PdfStatementParserTest {

    // מרכזי העמודות בדף לאומי אמיתי
    private fun date(y: Float, text: String) = PdfTextSpan(text, 496.5f, 546f, y)
    private fun desc(y: Float, text: String) = PdfTextSpan(text, 420.6f, 484f, y)
    private fun merged(y: Float, text: String) = PdfTextSpan(text, 416.7f, 546f, y)
    private fun debit(y: Float, text: String) = PdfTextSpan(text, 202f, 229.9f, y)
    private fun credit(y: Float, text: String) = PdfTextSpan(text, 316f, 340.8f, y)
    private fun balance(y: Float, text: String) = PdfTextSpan(text, 87f, 122.6f, y)
    private fun shekel(y: Float, x: Float) = PdfTextSpan("₪", x, x + 11f, y)

    private fun header(): List<PdfTextSpan> = listOf(
        PdfTextSpan("תאריך", 505.3f, 537f, 136.4f),
        PdfTextSpan("סוג תנועה", 408.3f, 459f, 136.4f),
        PdfTextSpan("זכות", 311f, 334f, 136.4f),
        PdfTextSpan("חובה", 197.3f, 223f, 136.4f),
        // "יתרה מצטברת" מגיעה מפוצלת לשלושה מקטעים סמוכים
        PdfTextSpan("י", 131.3f, 135f, 136.4f),
        PdfTextSpan("תרה", 109.1f, 131f, 136.4f),
        PdfTextSpan("מצטברת", 62.2f, 106f, 136.4f)
    )

    /** שורות פרטי החשבון שמעל הכותרת — מכילות תאריכים ואסור שייקלטו כעסקאות. */
    private fun metadata(): List<PdfTextSpan> = listOf(
        PdfTextSpan("תאריך", 516.3f, 548f, 44.7f),
        PdfTextSpan("הפקה:", 480.3f, 513f, 44.7f),
        PdfTextSpan("04.08.2026", 411.3f, 466f, 44.7f),
        PdfTextSpan("דף חשבון", 484.7f, 548f, 79.4f),
        PdfTextSpan(":לתקופה", 504.2f, 548f, 107.2f),
        PdfTextSpan("04.05.2026", 447.1f, 501f, 107.3f),
        PdfTextSpan("-", 438.7f, 445f, 107.3f),
        PdfTextSpan("04.08.2026", 381.3f, 436f, 107.3f)
    )

    /** שוליים תחתונים — הערה ומספר עמוד, בלי תאריך. */
    private fun footer(): List<PdfTextSpan> = listOf(
        PdfTextSpan("לא כולל תנועות שבוצעו היום ותנועות הממתינות לביצוע", 269f, 533f, 792f),
        PdfTextSpan("1", 52f, 56f, 816.5f),
        PdfTextSpan("/", 47f, 52f, 816.5f),
        PdfTextSpan("1", 43f, 47f, 816.5f)
    )

    private fun dayOf(timestamp: Long): Triple<Int, Int, Int> =
        Calendar.getInstance().apply { timeInMillis = timestamp }.let {
            Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
        }

    @Test
    fun `parses a leumi style statement with debit and credit columns`() {
        val spans = metadata() + header() + footer() + listOf(
            // תאריך ותיאור כמקטעים נפרדים
            date(160f, "02.08.2026"), desc(160f, "(כרטיס אשראי)כא"),
            debit(160f, "218.50"), shekel(160f, 190f),
            balance(160f, "7,036.67"), shekel(160f, 74f),
            // תאריך ותיאור ממוזגים למקטע אחד — הצורה הנפוצה בקובץ אמיתי
            merged(183.2f, "02.08.2026י-החזר נסיעות"),
            credit(183.2f, "39.00"), shekel(183.2f, 303f),
            balance(183.2f, "7,255.17"), shekel(183.2f, 76f)
        )

        val statement = PdfStatementParser.parse(spans).getOrThrow()

        assertEquals(2, statement.rows.size)
        assertEquals(StatementKind.BANK_ACCOUNT, statement.kind)
        assertEquals("חובה הופך לשלילי", -218.50, statement.rows[0].amount, 0.001)
        assertEquals("זכות הופך לחיובי", 39.00, statement.rows[1].amount, 0.001)
        assertEquals(Triple(2026, Calendar.AUGUST, 2), dayOf(statement.rows[0].timestamp))
    }

    @Test
    fun `splits the date off a merged date and description span`() {
        val spans = header() + listOf(
            merged(160f, "15.06.2026י-החזר הוצאות"),
            credit(160f, "10,214.45"), balance(160f, "20,176.40")
        )

        val row = PdfStatementParser.parse(spans).getOrThrow().rows.single()

        assertEquals(Triple(2026, Calendar.JUNE, 15), dayOf(row.timestamp))
        assertEquals("התיאור נשאר בלי התאריך", "י-החזר הוצאות", row.merchant)
    }

    @Test
    fun `keeps hebrew words intact so keyword matching still works`() {
        val spans = header() + listOf(
            merged(160f, "03.07.2026כ.הפק' מזומן י"),
            credit(160f, "2,900.00"), balance(160f, "7,253.69")
        )

        val row = PdfStatementParser.parse(spans).getOrThrow().rows.single()
        assertTrue("המילה 'מזומן' חייבת להישאר רציפה", row.merchant.contains("מזומן"))
    }

    @Test
    fun `ignores the running balance column`() {
        val spans = header() + listOf(
            merged(160f, "05.07.2026תשלום"),
            debit(160f, "120.00"), balance(160f, "9,999.99")
        )

        // אילו היתרה הייתה נספרת כסכום, התוצאה הייתה 9,999.99 ולא 120
        assertEquals(-120.0, PdfStatementParser.parse(spans).getOrThrow().rows.single().amount, 0.001)
    }

    @Test
    fun `assigns a wide amount to the nearest column`() {
        // ‎15,000.00 רחב בהרבה מ-‎0.44 ולכן חורג מטווח הכותרת — השיוך חייב להיות לפי מרכז
        val spans = header() + listOf(
            merged(160f, "02.07.2026העברה"),
            PdfTextSpan("15,000.00", 194f, 238.5f, 160f),
            balance(160f, "4,354.46")
        )

        assertEquals(-15000.0, PdfStatementParser.parse(spans).getOrThrow().rows.single().amount, 0.001)
    }

    @Test
    fun `ignores account details above the header even when they contain dates`() {
        val spans = metadata() + header() + listOf(
            merged(160f, "01.07.2026תשלום"), debit(160f, "50.00")
        )

        val statement = PdfStatementParser.parse(spans).getOrThrow()
        assertEquals("רק שורה אחת אמיתית, למרות התאריכים בכותרת העמוד", 1, statement.rows.size)
    }

    @Test
    fun `skips footer lines without a date`() {
        val spans = header() + footer() + listOf(
            merged(160f, "01.07.2026תשלום"), debit(160f, "50.00")
        )

        val statement = PdfStatementParser.parse(spans).getOrThrow()
        assertEquals(1, statement.rows.size)
        assertTrue("שורות השוליים נספרות כמדולגות", statement.skippedRows >= 1)
    }

    @Test
    fun `fails when there is no recognisable header`() {
        val spans = listOf(
            PdfTextSpan("שלום", 500f, 540f, 100f),
            PdfTextSpan("עולם", 400f, 440f, 100f)
        )

        val error = PdfStatementParser.parse(spans).exceptionOrNull()
        assertEquals(ImportError.NO_HEADER, (error as ImportException).error)
    }

    @Test
    fun `fails on an empty page`() {
        val error = PdfStatementParser.parse(emptyList()).exceptionOrNull()
        assertEquals(ImportError.EMPTY_FILE, (error as ImportException).error)
    }
}
