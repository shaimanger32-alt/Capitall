package com.shai.capitall

import com.shai.capitall.data.model.Transaction
import com.shai.capitall.util.csv.ImportDeduplicator
import com.shai.capitall.util.csv.ImportRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ImportDeduplicatorTest {

    private fun ts(year: Int, month0: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, month0, day, hour, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun row(merchant: String, amount: Double, timestamp: Long) =
        ImportRow(timestamp, merchant, amount)

    private fun tx(merchant: String, amount: Double, timestamp: Long) =
        Transaction(merchant = merchant, amount = amount, timestamp = timestamp, category = "food")

    @Test
    fun `flags a row that already exists`() {
        val existing = listOf(tx("שופרסל", -320.5, ts(2026, Calendar.JANUARY, 5)))
        val rows = listOf(row("שופרסל", -320.5, ts(2026, Calendar.JANUARY, 5)))

        assertEquals(listOf(true), ImportDeduplicator.findDuplicates(rows, existing))
    }

    @Test
    fun `ignores the time of day so a manual entry matches a bank row`() {
        // עסקה שהוזנה ידנית נושאת שעה, בעוד קובץ הבנק מציין תאריך בלבד
        val existing = listOf(tx("שופרסל", -320.5, ts(2026, Calendar.JANUARY, 5, hour = 19)))
        val rows = listOf(row("שופרסל", -320.5, ts(2026, Calendar.JANUARY, 5, hour = 12)))

        assertEquals(listOf(true), ImportDeduplicator.findDuplicates(rows, existing))
    }

    @Test
    fun `does not flag a different day or a different amount`() {
        val existing = listOf(tx("שופרסל", -320.5, ts(2026, Calendar.JANUARY, 5)))
        val rows = listOf(
            row("שופרסל", -320.5, ts(2026, Calendar.JANUARY, 6)),
            row("שופרסל", -100.0, ts(2026, Calendar.JANUARY, 5))
        )

        assertEquals(listOf(false, false), ImportDeduplicator.findDuplicates(rows, existing))
    }

    @Test
    fun `counts multiplicity instead of collapsing repeats`() {
        // המשתמש באמת קנה פעמיים באותו סכום באותו יום, אבל רק אחת נרשמה — השנייה חייבת להיכנס
        val existing = listOf(tx("ארומה", -18.0, ts(2026, Calendar.JANUARY, 5)))
        val rows = listOf(
            row("ארומה", -18.0, ts(2026, Calendar.JANUARY, 5)),
            row("ארומה", -18.0, ts(2026, Calendar.JANUARY, 5))
        )

        assertEquals(listOf(true, false), ImportDeduplicator.findDuplicates(rows, existing))
    }

    @Test
    fun `re-importing the same file flags every row`() {
        val rows = listOf(
            row("שופרסל", -320.5, ts(2026, Calendar.JANUARY, 5)),
            row("פז", -300.0, ts(2026, Calendar.JANUARY, 7))
        )
        val existing = rows.map { tx(it.merchant, it.amount, it.timestamp) }

        assertEquals(listOf(true, true), ImportDeduplicator.findDuplicates(rows, existing))
    }

    @Test
    fun `income and expense of the same size are not confused`() {
        val existing = listOf(tx("העברה", 500.0, ts(2026, Calendar.JANUARY, 5)))
        val rows = listOf(row("העברה", -500.0, ts(2026, Calendar.JANUARY, 5)))

        assertEquals(listOf(false), ImportDeduplicator.findDuplicates(rows, existing))
    }
}
