package com.shai.capitall

import com.shai.capitall.data.model.Transaction
import com.shai.capitall.util.csv.CategorySource
import com.shai.capitall.util.csv.MerchantCategorizer
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantCategorizerTest {

    private fun tx(merchant: String, category: String) =
        Transaction(merchant = merchant, category = category, amount = -50.0)

    @Test
    fun `learns the category the user chose for a merchant`() {
        val categorizer = MerchantCategorizer(listOf(tx("קפה נמרוד", "Entertainment")))

        val result = categorizer.categorize("קפה נמרוד", -18.0)

        assertEquals("Entertainment", result.category)
        assertEquals(CategorySource.HISTORY, result.source)
    }

    @Test
    fun `user history beats the built-in rules`() {
        // "שופרסל" הוא כלל מובנה למזון, אבל המשתמש סיווג אותו אחרת — הבחירה שלו גוברת
        val categorizer = MerchantCategorizer(listOf(tx("שופרסל", "shopping")))

        val result = categorizer.categorize("שופרסל", -240.0)

        assertEquals("shopping", result.category)
        assertEquals(CategorySource.HISTORY, result.source)
    }

    @Test
    fun `matches history even when the bank appends a branch name`() {
        val categorizer = MerchantCategorizer(listOf(tx("יוחננוף", "food")))

        val result = categorizer.categorize("יוחננוף סניף חיפה", -310.0)

        assertEquals("food", result.category)
        assertEquals(CategorySource.HISTORY, result.source)
    }

    @Test
    fun `uses the most frequent category when history disagrees`() {
        val categorizer = MerchantCategorizer(
            listOf(tx("פז", "Transport"), tx("פז", "Transport"), tx("פז", "food"))
        )

        assertEquals("Transport", categorizer.categorize("פז", -200.0).category)
    }

    @Test
    fun `falls back to the built-in merchant rules`() {
        val categorizer = MerchantCategorizer()

        assertEquals("food", categorizer.categorize("רמי לוי", -240.0).category)
        assertEquals("Transport", categorizer.categorize("סונול", -200.0).category)
        assertEquals("Housing", categorizer.categorize("חברת החשמל", -450.0).category)
        assertEquals("Entertainment", categorizer.categorize("NETFLIX.COM", -55.0).category)
        assertEquals(CategorySource.RULE, categorizer.categorize("רמי לוי", -240.0).source)
    }

    @Test
    fun `specific rules win over broader ones`() {
        // "סופר פארם" מכיל את "סופר" — חייב להיות בריאות ולא מזון
        assertEquals("Health", MerchantCategorizer().categorize("סופר פארם", -80.0).category)
    }

    @Test
    fun `unknown merchants fall back by the sign of the amount`() {
        val categorizer = MerchantCategorizer()

        val expense = categorizer.categorize("עסק לא מוכר", -75.0)
        assertEquals(MerchantCategorizer.FALLBACK_EXPENSE, expense.category)
        assertEquals(CategorySource.FALLBACK, expense.source)

        val income = categorizer.categorize("עסק לא מוכר", 900.0)
        assertEquals(MerchantCategorizer.FALLBACK_INCOME, income.category)
        assertEquals(CategorySource.FALLBACK, income.source)
    }

    @Test
    fun `an expense rule never applies to an incoming amount`() {
        // זיכוי מבית עסק שמוכר ככלל הוצאה (החזר ממסעדה) — חייב ליפול לקטגוריית הכנסה
        val result = MerchantCategorizer().categorize("מסעדת טורקיז", 821.0)

        assertEquals(MerchantCategorizer.FALLBACK_INCOME, result.category)
        assertEquals(CategorySource.FALLBACK, result.source)
    }

    @Test
    fun `history is ignored when its direction does not match`() {
        val categorizer = MerchantCategorizer(listOf(tx("מסעדת טורקיז", "food")))

        assertEquals("food", categorizer.categorize("מסעדת טורקיז", -120.0).category)
        assertEquals(
            "אותו בית עסק בכיוון ההפוך לא יורש קטגוריית הוצאה",
            MerchantCategorizer.FALLBACK_INCOME,
            categorizer.categorize("מסעדת טורקיז", 821.0).category
        )
    }

    @Test
    fun `income rules still match incoming amounts`() {
        assertEquals("salary", MerchantCategorizer().categorize("משכורת אוגוסט", 12500.0).category)
    }

    @Test
    fun `blank merchant falls back without crashing`() {
        val result = MerchantCategorizer(listOf(tx("", "food"))).categorize("", -20.0)
        assertEquals(MerchantCategorizer.FALLBACK_EXPENSE, result.category)
    }
}
