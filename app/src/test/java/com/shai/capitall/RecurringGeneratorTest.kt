package com.shai.capitall

import com.shai.capitall.data.model.Transaction
import com.shai.capitall.util.RecurringGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringGeneratorTest {

    private fun ts(year: Int, month0: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month0, day, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val now = ts(2026, Calendar.MARCH, 20)

    private fun tx(merchant: String, amount: Double, timestamp: Long, recurring: Boolean) =
        Transaction(merchant = merchant, category = "Housing", amount = amount, timestamp = timestamp, isRecurring = recurring)

    @Test
    fun `generates missing occurrence for current month`() {
        val existing = listOf(tx("Rent", -3000.0, ts(2026, Calendar.FEBRUARY, 5), recurring = true))
        val due = RecurringGenerator.dueOccurrences(existing, now)
        assertEquals(1, due.size)
        val cal = Calendar.getInstance().apply { timeInMillis = due.first().timestamp }
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals("שומר על יום-בחודש של התבנית", 5, cal.get(Calendar.DAY_OF_MONTH))
        assertTrue(due.first().isRecurring)
        assertEquals("", due.first().id)
    }

    @Test
    fun `skips when occurrence already exists this month`() {
        val existing = listOf(
            tx("Rent", -3000.0, ts(2026, Calendar.FEBRUARY, 5), recurring = true),
            tx("Rent", -3000.0, ts(2026, Calendar.MARCH, 5), recurring = true)
        )
        assertTrue(RecurringGenerator.dueOccurrences(existing, now).isEmpty())
    }

    @Test
    fun `ignores non-recurring transactions`() {
        val existing = listOf(tx("Coffee", -15.0, ts(2026, Calendar.FEBRUARY, 5), recurring = false))
        assertTrue(RecurringGenerator.dueOccurrences(existing, now).isEmpty())
    }

    @Test
    fun `distinct recurring signatures each generate one occurrence`() {
        val existing = listOf(
            tx("Rent", -3000.0, ts(2026, Calendar.FEBRUARY, 5), recurring = true),
            tx("Salary", 12000.0, ts(2026, Calendar.FEBRUARY, 1), recurring = true)
        )
        assertEquals(2, RecurringGenerator.dueOccurrences(existing, now).size)
    }
}
