package com.shai.capitall.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.shai.capitall.R


enum class CategoryScope { ASSET, LIABILITY, TRANSACTION_INCOME, TRANSACTION_EXPENSE }

/**
 * קבוצת-על ויזואלית לבחירת קטגוריה (מסך [com.shai.capitall.ui.selectcategory.SelectCategoryActivity]).
 * הקבוצות אינן משנות את מודל הנתונים — הן רק מארגנות את הקטגוריות הקיימות לאקורדיון צבעוני.
 * הסדר כאן הוא סדר התצוגה במסך.
 */
enum class CategoryGroup(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val colorHex: String
) {
    CASH_EQUIVALENTS(R.string.category_group_cash, R.drawable.ic_group_cash, "#17A673"),
    INVESTMENT(R.string.category_group_investment, R.drawable.ic_group_investment, "#7C4DFF"),
    PROPERTY(R.string.category_group_property, R.drawable.ic_group_property, "#2E7DF7"),
    OTHER_ASSETS(R.string.category_group_other, R.drawable.ic_group_other, "#6B7280"),
    LIABILITY(R.string.category_group_liability, R.drawable.ic_group_liability, "#E23D3D")
}

data class CategoryDefinition(
    val key: String,
    @StringRes val labelRes: Int,
    val scopes: Set<CategoryScope>,
    val colorHex: String,
    @StringRes val recurringOptionLabelRes: Int? = null,
    val group: CategoryGroup? = null,
    /** אייקון ייעודי לקטגוריה; ברירת מחדל = אייקון "אחר" הגנרי. */
    @DrawableRes val iconRes: Int = R.drawable.ic_cat_other
)
