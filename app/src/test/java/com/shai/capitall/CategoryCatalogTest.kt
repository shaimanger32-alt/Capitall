package com.shai.capitall

import com.shai.capitall.data.model.CategoryGroup
import com.shai.capitall.data.model.CategoryScope
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CategoryMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCatalogTest {

    private fun expenseKeys() = CategoryCatalog.forScope(CategoryScope.TRANSACTION_EXPENSE).map { it.key }
    private fun incomeKeys() = CategoryCatalog.forScope(CategoryScope.TRANSACTION_INCOME).map { it.key }

    @Test
    fun `consolidated expense categories replace the old granular ones`() {
        val keys = expenseKeys()
        assertTrue("food" in keys)
        assertTrue("Housing" in keys)
        assertTrue("other_expense" in keys)
        // הקטגוריות הישנות אוחדו והוסרו מהקטלוג
        listOf("Restaurants", "Delivery", "Supermarket", "Utilities").forEach {
            assertTrue("$it should no longer be a catalog category", it !in keys)
            assertNull(CategoryCatalog.byKey(it))
        }
    }

    @Test
    fun `income categories include salary and legacy income`() {
        val keys = incomeKeys()
        assertTrue("salary" in keys)
        assertTrue("Income" in keys) // legacy key kept as "Other income"
        assertNotNull(CategoryCatalog.byKey("salary"))
    }

    @Test
    fun `grouped entry buckets cover asset and liability categories`() {
        val grouped = CategoryCatalog.groupedForEntry().toMap()
        assertTrue(grouped.containsKey(CategoryGroup.LIABILITY))
        assertTrue(grouped[CategoryGroup.LIABILITY]!!.any { it.key == "mortgage" })
        assertTrue(grouped[CategoryGroup.INVESTMENT]!!.any { it.key == "stocks_investments" })
    }

    @Test
    fun `every migration target maps to a real catalog category`() {
        CategoryMigration.LEGACY_MAP.values.distinct().forEach { newKey ->
            assertNotNull("migration target '$newKey' must exist in catalog", CategoryCatalog.byKey(newKey))
        }
    }

    @Test
    fun `migration maps legacy food and utilities keys`() {
        assertEquals("food", CategoryMigration.LEGACY_MAP["Restaurants"])
        assertEquals("food", CategoryMigration.LEGACY_MAP["Supermarket"])
        assertEquals("Housing", CategoryMigration.LEGACY_MAP["Utilities"])
    }
}
