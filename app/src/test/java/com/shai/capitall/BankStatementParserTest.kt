package com.shai.capitall

import com.shai.capitall.util.csv.BankStatementParser
import com.shai.capitall.util.csv.CsvReader
import com.shai.capitall.util.csv.FileSignature
import com.shai.capitall.util.csv.ImportError
import com.shai.capitall.util.csv.ImportException
import com.shai.capitall.util.csv.StatementKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.util.Calendar

class BankStatementParserTest {

    private fun dayOf(timestamp: Long): Triple<Int, Int, Int> =
        Calendar.getInstance().apply { timeInMillis = timestamp }.let {
            Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
        }

    // ---------- דף חשבון בנק (חובה/זכות) ----------

    @Test
    fun `parses bank statement with debit and credit columns`() {
        val csv = """
            דוח תנועות בחשבון
            מתאריך 01/01/2026 עד 31/01/2026

            תאריך,תיאור,חובה,זכות,יתרה
            05/01/2026,שופרסל דיל,320.50,,12000.00
            07/01/2026,משכורת ינואר,,12500.00,24500.00
        """.trimIndent()

        val statement = BankStatementParser.parse(csv).getOrThrow()

        assertEquals(2, statement.rows.size)
        assertEquals(StatementKind.BANK_ACCOUNT, statement.kind)
        assertEquals("חובה הופך לסכום שלילי", -320.50, statement.rows[0].amount, 0.001)
        assertEquals("זכות הופך לסכום חיובי", 12500.0, statement.rows[1].amount, 0.001)
        assertEquals("שופרסל דיל", statement.rows[0].merchant)
        assertEquals(Triple(2026, Calendar.JANUARY, 5), dayOf(statement.rows[0].timestamp))
    }

    @Test
    fun `ignores balance column when looking for the amount`() {
        // "יתרה" היא יתרה מצטברת — אם היא תיבחר כעמודת הסכום כל העסקאות יהיו שגויות
        val csv = """
            תאריך,פירוט,יתרה,סכום
            05/01/2026,קניה,9000.00,-120.00
        """.trimIndent()

        val statement = BankStatementParser.parse(csv).getOrThrow()
        assertEquals(-120.0, statement.rows.single().amount, 0.001)
    }

    // ---------- פירוט אשראי (עמודת סכום חיובית אחת) ----------

    @Test
    fun `treats all-positive single amount column as credit card charges`() {
        val csv = """
            תאריך עסקה,שם בית עסק,סכום חיוב
            03/02/2026,"רמי לוי, שיווק השקמה",250.00
            04/02/2026,פז תדלוק,300.00
        """.trimIndent()

        val statement = BankStatementParser.parse(csv).getOrThrow()

        assertEquals(StatementKind.CREDIT_CARD, statement.kind)
        assertTrue("חיובי אשראי נשמרים כהוצאות", statement.rows.all { it.amount < 0 })
        assertEquals(-250.0, statement.rows[0].amount, 0.001)
        assertEquals("פסיק בתוך מרכאות אינו מפריד", "רמי לוי, שיווק השקמה", statement.rows[0].merchant)
    }

    @Test
    fun `keeps signs when single amount column already has negatives`() {
        val csv = """
            Date,Description,Amount
            03/02/2026,Coffee,-18.00
            04/02/2026,Refund,52.00
        """.trimIndent()

        val statement = BankStatementParser.parse(csv).getOrThrow()

        assertEquals(StatementKind.BANK_ACCOUNT, statement.kind)
        assertEquals(-18.0, statement.rows[0].amount, 0.001)
        assertEquals(52.0, statement.rows[1].amount, 0.001)
    }

    // ---------- עמידות ----------

    @Test
    fun `skips summary rows without a valid date`() {
        val csv = """
            תאריך,תיאור,סכום
            05/01/2026,קניה,-100.00
            סה"כ,,-100.00
        """.trimIndent()

        val statement = BankStatementParser.parse(csv).getOrThrow()
        assertEquals(1, statement.rows.size)
        assertEquals(1, statement.skippedRows)
    }

    @Test
    fun `detects semicolon delimiter`() {
        val csv = """
            תאריך;תיאור;סכום
            05/01/2026;קניה;-100.00
        """.trimIndent()

        assertEquals(';', CsvReader.detectDelimiter(csv))
        assertEquals(1, BankStatementParser.parse(csv).getOrThrow().rows.size)
    }

    @Test
    fun `fails with NO_HEADER when there is no recognisable header`() {
        val csv = """
            שלום,עולם
            אלף,בית
        """.trimIndent()

        val error = BankStatementParser.parse(csv).exceptionOrNull()
        assertEquals(ImportError.NO_HEADER, (error as ImportException).error)
    }

    @Test
    fun `fails with EMPTY_FILE on blank input`() {
        val error = BankStatementParser.parse("   \n  ").exceptionOrNull()
        assertEquals(ImportError.EMPTY_FILE, (error as ImportException).error)
    }

    // ---------- פענוח ערכים ----------

    @Test
    fun `parses amounts with separators currency signs and negative notations`() {
        assertEquals(1234.56, BankStatementParser.parseAmount("1,234.56")!!, 0.001)
        assertEquals(-99.9, BankStatementParser.parseAmount("-99.90")!!, 0.001)
        assertEquals(-99.9, BankStatementParser.parseAmount("99.90-")!!, 0.001)
        assertEquals(-99.9, BankStatementParser.parseAmount("(99.90)")!!, 0.001)
        assertEquals(50.0, BankStatementParser.parseAmount("₪50.00")!!, 0.001)
        assertEquals(50.0, BankStatementParser.parseAmount("‎50.00")!!, 0.001)
        assertNull(BankStatementParser.parseAmount(""))
        assertNull(BankStatementParser.parseAmount("לא מספר"))
    }

    @Test
    fun `parses the supported date formats`() {
        assertEquals(Triple(2026, Calendar.MARCH, 12), dayOf(BankStatementParser.parseDate("12/03/2026")!!))
        assertEquals(Triple(2026, Calendar.MARCH, 12), dayOf(BankStatementParser.parseDate("12-03-2026")!!))
        assertEquals(Triple(2026, Calendar.MARCH, 12), dayOf(BankStatementParser.parseDate("12.03.2026")!!))
        assertEquals(Triple(2026, Calendar.MARCH, 12), dayOf(BankStatementParser.parseDate("2026-03-12")!!))
        assertEquals("מתעלם מהשעה", Triple(2026, Calendar.MARCH, 12), dayOf(BankStatementParser.parseDate("12/03/2026 14:33")!!))
        assertNull(BankStatementParser.parseDate("לא תאריך"))
    }

    // ---------- קידוד ----------

    @Test
    fun `detects excel and pdf files by signature`() {
        val xlsx = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(20)
        val xls = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte()) + ByteArray(20)
        val pdf = "%PDF-1.7\n".toByteArray()
        val csv = "תאריך,סכום\n05/01/2026,-10.00".toByteArray()

        assertEquals(FileSignature.SPREADSHEET, CsvReader.detectSignature(xlsx))
        assertEquals(FileSignature.SPREADSHEET, CsvReader.detectSignature(xls))
        assertEquals(FileSignature.PDF, CsvReader.detectSignature(pdf))
        assertEquals(FileSignature.TEXT, CsvReader.detectSignature(csv))
    }

    @Test
    fun `decodes windows-1255 hebrew files`() {
        val text = "תאריך,תיאור,סכום"
        val bytes = text.toByteArray(Charset.forName("windows-1255"))
        assertEquals(text, CsvReader.decode(bytes))
    }

    @Test
    fun `strips utf8 bom so the first header is not polluted`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "תאריך,סכום".toByteArray(Charsets.UTF_8)
        assertEquals("תאריך,סכום", CsvReader.decode(bytes))
    }
}
