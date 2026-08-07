package com.shai.capitall

import com.shai.capitall.data.model.Transaction
import com.shai.capitall.util.SpaceBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceBalanceTest {

    private val shay = "uid-shay"
    private val dana = "uid-dana"
    private val yoav = "uid-yoav"

    private fun expense(payer: String, amount: Double) =
        Transaction(userId = payer, paidBy = payer, amount = -amount, spaceId = "SP1")

    private fun income(payer: String, amount: Double) =
        Transaction(userId = payer, paidBy = payer, amount = amount, spaceId = "SP1")

    private fun netOf(balances: List<SpaceBalance.MemberBalance>, userId: String) =
        balances.first { it.userId == userId }.net

    @Test
    fun `one payer two members splits in half`() {
        val balances = SpaceBalance.balances(listOf(expense(shay, 3000.0)), listOf(shay, dana))

        assertEquals("המשלם זכאי למחצית", 1500.0, netOf(balances, shay), 0.001)
        assertEquals("השני חייב מחצית", -1500.0, netOf(balances, dana), 0.001)
    }

    @Test
    fun `member with no transactions still appears`() {
        // מי שלא שילם כלום הוא בדיוק מי שחייב — אסור שייעלם מהמאזן
        val balances = SpaceBalance.balances(listOf(expense(shay, 900.0)), listOf(shay, dana, yoav))

        assertEquals(3, balances.size)
        assertEquals(-300.0, netOf(balances, yoav), 0.001)
    }

    @Test
    fun `equal spending settles to zero`() {
        val balances = SpaceBalance.balances(
            listOf(expense(shay, 500.0), expense(dana, 500.0)),
            listOf(shay, dana)
        )

        assertEquals(0.0, netOf(balances, shay), 0.001)
        assertEquals(0.0, netOf(balances, dana), 0.001)
        assertTrue(SpaceBalance.settlements(balances).isEmpty())
    }

    @Test
    fun `shared income owed back to the others`() {
        // דנה קיבלה 1,000 משותפים לכיסה — היא חייבת לשי את חלקו
        val balances = SpaceBalance.balances(listOf(income(dana, 1000.0)), listOf(shay, dana))

        assertEquals(-500.0, netOf(balances, dana), 0.001)
        assertEquals(500.0, netOf(balances, shay), 0.001)
    }

    @Test
    fun `balances always sum to zero`() {
        val balances = SpaceBalance.balances(
            listOf(expense(shay, 1200.0), expense(dana, 300.0), income(yoav, 150.0)),
            listOf(shay, dana, yoav)
        )

        assertEquals(0.0, balances.sumOf { it.net }, 0.001)
    }

    @Test
    fun `settlement transfers close every debt`() {
        val balances = SpaceBalance.balances(
            listOf(expense(shay, 1200.0), expense(dana, 300.0)),
            listOf(shay, dana, yoav)
        )
        val transfers = SpaceBalance.settlements(balances)

        // כל חייב מסיים באפס אחרי ההעברות
        val remaining = balances.associate { it.userId to it.net }.toMutableMap()
        transfers.forEach { transfer ->
            remaining[transfer.fromUserId] = remaining.getValue(transfer.fromUserId) + transfer.amount
            remaining[transfer.toUserId] = remaining.getValue(transfer.toUserId) - transfer.amount
        }
        remaining.values.forEach { assertEquals(0.0, it, 0.01) }
    }

    @Test
    fun `settlement uses at most n minus one transfers`() {
        val balances = SpaceBalance.balances(
            listOf(expense(shay, 600.0), expense(dana, 300.0)),
            listOf(shay, dana, yoav)
        )

        assertTrue(SpaceBalance.settlements(balances).size <= balances.size - 1)
    }

    @Test
    fun `transaction from a former member counts in the total but not against him`() {
        // מי שעזב אינו ברשימת החברים; ההוצאה שלו עדיין מחולקת בין הנשארים
        val transactions = listOf(expense(shay, 400.0), expense("uid-left", 600.0))
        val balances = SpaceBalance.balances(transactions, listOf(shay, dana))

        assertEquals(2, balances.size)
        assertEquals("סך 1,000 מחולק לשניים", 500.0, balances.first().fairShare, 0.001)
        assertEquals(-100.0, netOf(balances, shay), 0.001)
    }

    @Test
    fun `empty member list yields no balances`() {
        assertTrue(SpaceBalance.balances(listOf(expense(shay, 100.0)), emptyList()).isEmpty())
    }
}
