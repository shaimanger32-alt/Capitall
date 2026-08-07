package com.shai.capitall.util

import android.content.Context
import com.shai.capitall.R
import com.shai.capitall.data.model.CategoryDefinition
import com.shai.capitall.data.model.CategoryGroup
import com.shai.capitall.data.model.CategoryScope
import com.shai.capitall.data.model.CategoryScope.ASSET
import com.shai.capitall.data.model.CategoryScope.LIABILITY
import com.shai.capitall.data.model.CategoryScope.TRANSACTION_EXPENSE
import com.shai.capitall.data.model.CategoryScope.TRANSACTION_INCOME

object CategoryCatalog {

    val all: List<CategoryDefinition> = listOf(
        // Expense categories — consolidated general buckets (fewer, broader).
        // Keys "Transport"/"Housing"/"Health"/"Entertainment" kept from the old set so existing
        // transactions keep their category; "food"/"shopping"/"other_expense" are new.
        CategoryDefinition("food", R.string.category_food, setOf(TRANSACTION_EXPENSE), "#E67E22", iconRes = R.drawable.ic_cat_food),
        CategoryDefinition("Transport", R.string.category_transport, setOf(TRANSACTION_EXPENSE), "#7F8C8D", iconRes = R.drawable.ic_cat_transport),
        CategoryDefinition("Housing", R.string.category_housing_bills, setOf(TRANSACTION_EXPENSE), "#3498DB", iconRes = R.drawable.ic_cat_housing),
        CategoryDefinition("shopping", R.string.category_shopping, setOf(TRANSACTION_EXPENSE), "#9B59B6", iconRes = R.drawable.ic_cat_shopping),
        CategoryDefinition("Health", R.string.category_health, setOf(TRANSACTION_EXPENSE), "#EC407A", iconRes = R.drawable.ic_cat_health),
        CategoryDefinition("Entertainment", R.string.category_entertainment, setOf(TRANSACTION_EXPENSE), "#E74C3C", iconRes = R.drawable.ic_cat_entertainment),
        CategoryDefinition("other_expense", R.string.category_other_expense, setOf(TRANSACTION_EXPENSE), "#1ABC9C", iconRes = R.drawable.ic_cat_other),

        // Income categories. Legacy key "Income" kept (relabeled "Other income") for old data.
        CategoryDefinition("salary", R.string.category_salary, setOf(TRANSACTION_INCOME), "#2ECC71", iconRes = R.drawable.ic_cat_salary),
        CategoryDefinition("business_income", R.string.category_business_income, setOf(TRANSACTION_INCOME), "#27AE60", iconRes = R.drawable.ic_cat_business),
        CategoryDefinition("investment_income", R.string.category_investment_income, setOf(TRANSACTION_INCOME), "#16A085", iconRes = R.drawable.ic_cat_investment),
        CategoryDefinition("rent_income", R.string.category_rent_income, setOf(TRANSACTION_INCOME), "#1ABC9C", iconRes = R.drawable.ic_cat_rent),
        CategoryDefinition("gift_income", R.string.category_gift_income, setOf(TRANSACTION_INCOME), "#58D68D", iconRes = R.drawable.ic_cat_gift),
        CategoryDefinition("Income", R.string.category_income_other, setOf(TRANSACTION_INCOME), "#82E0AA", iconRes = R.drawable.ic_cat_other),

        // Asset categories
        CategoryDefinition(
            "real_estate", R.string.category_real_estate, setOf(ASSET), "#5DADE2",
            R.string.category_option_rental_income, CategoryGroup.PROPERTY, iconRes = R.drawable.ic_cat_realestate),
        CategoryDefinition(
            "stocks_investments", R.string.category_stocks_investments, setOf(ASSET), "#48C9B0",
            R.string.category_option_dividend_income, CategoryGroup.INVESTMENT, iconRes = R.drawable.ic_cat_stocks),
        CategoryDefinition(
            "cash_savings", R.string.category_cash_savings, setOf(ASSET), "#58D68D",
            R.string.category_option_interest_income, CategoryGroup.CASH_EQUIVALENTS, iconRes = R.drawable.ic_cat_savings),
        CategoryDefinition("vehicle", R.string.category_vehicle, setOf(ASSET), "#F5B041", group = CategoryGroup.PROPERTY, iconRes = R.drawable.ic_cat_vehicle),
        CategoryDefinition("crypto", R.string.category_crypto, setOf(ASSET), "#AF7AC5", group = CategoryGroup.INVESTMENT, iconRes = R.drawable.ic_cat_crypto),
        CategoryDefinition("pension_retirement", R.string.category_pension_retirement, setOf(ASSET), "#5499C7", group = CategoryGroup.INVESTMENT, iconRes = R.drawable.ic_cat_pension),
        CategoryDefinition("business_equity", R.string.category_business_equity, setOf(ASSET), "#DC7633", group = CategoryGroup.INVESTMENT, iconRes = R.drawable.ic_cat_equity),
        CategoryDefinition(
            "bonds", R.string.category_bonds, setOf(ASSET), "#45B39D",
            R.string.category_option_interest_income, CategoryGroup.INVESTMENT, iconRes = R.drawable.ic_cat_bonds),
        CategoryDefinition("foreign_currency", R.string.category_foreign_currency, setOf(ASSET), "#7FB3D5", group = CategoryGroup.CASH_EQUIVALENTS, iconRes = R.drawable.ic_cat_forex),
        CategoryDefinition("other_assets", R.string.category_other_assets, setOf(ASSET), "#85929E", group = CategoryGroup.OTHER_ASSETS, iconRes = R.drawable.ic_cat_otherassets),

        // Liability categories
        CategoryDefinition(
            "mortgage", R.string.category_mortgage, setOf(LIABILITY), "#EC7063",
            R.string.category_option_monthly_payment, CategoryGroup.LIABILITY, iconRes = R.drawable.ic_cat_mortgage),
        CategoryDefinition(
            "loan", R.string.category_loan, setOf(LIABILITY), "#E59866",
            R.string.category_option_monthly_payment, CategoryGroup.LIABILITY, iconRes = R.drawable.ic_cat_loan),
        CategoryDefinition("credit_card", R.string.category_credit_card, setOf(LIABILITY), "#CD6155", group = CategoryGroup.LIABILITY, iconRes = R.drawable.ic_cat_creditcard),
        CategoryDefinition(
            "student_loan", R.string.category_student_loan, setOf(LIABILITY), "#AF601A",
            R.string.category_option_monthly_payment, CategoryGroup.LIABILITY, iconRes = R.drawable.ic_cat_studentloan),
        CategoryDefinition(
            "credit_line", R.string.category_credit_line, setOf(LIABILITY), "#A93226",
            R.string.category_option_monthly_payment, CategoryGroup.LIABILITY, iconRes = R.drawable.ic_cat_creditline),
        CategoryDefinition("other_liabilities", R.string.category_other_liabilities, setOf(LIABILITY), "#909497", group = CategoryGroup.LIABILITY, iconRes = R.drawable.ic_cat_otherliabilities)
    )

    /**
     * הקטגוריות של המאזן (נכסים + התחייבויות) מקובצות לקבוצות-על לפי סדר התצוגה,
     * לשימוש מסך בחירת הקטגוריה. קטגוריות עסקה (הכנסה/הוצאה) אינן נכללות כאן.
     */
    fun groupedForEntry(): List<Pair<CategoryGroup, List<CategoryDefinition>>> =
        CategoryGroup.entries.mapNotNull { group ->
            val members = all.filter { it.group == group }
            if (members.isEmpty()) null else group to members
        }

    fun forScope(scope: CategoryScope): List<CategoryDefinition> = all.filter { scope in it.scopes }

    fun byKey(key: String): CategoryDefinition? = all.find { it.key == key }

    fun colorFor(key: String): String = byKey(key)?.colorHex ?: "#B0B3B8"

    /** האייקון של הקטגוריה; לקטגוריות מותאמות אישית מוחזר האייקון הגנרי. */
    fun iconFor(key: String): Int = byKey(key)?.iconRes ?: R.drawable.ic_cat_other

    fun labelFor(context: Context, key: String): String =
        byKey(key)?.let { context.getString(it.labelRes) } ?: key
}
