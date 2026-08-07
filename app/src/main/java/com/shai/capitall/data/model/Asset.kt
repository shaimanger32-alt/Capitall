package com.shai.capitall.data.model

data class Asset(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: AssetType = AssetType.MANUAL,
    val category: String = "",
    val value: Double = 0.0,
    val symbol: String? = null,      // רלוונטי ל-STOCK/CRYPTO
    val quantity: Double? = null,    // רלוונטי ל-STOCK/CRYPTO
    // מטבע הנקיבה של value — רלוונטי לקטגוריית מט"ח ("USD"/"EUR"/"ILS").
    // null = שקל (כל הנכסים הישנים), ולכן ההמרה שקופה עבורם.
    val currency: String? = null,
    val recurringIncomeAmount: Double? = null, // הערכת הכנסה חודשית חוזרת (למשל שכירות), רלוונטי לפי קטגוריה
    val annualRate: Double? = null, // שיעור שינוי שנתי מותאם אישית (null = ממוצע הקטגוריה כברירת מחדל)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AssetType {
    MANUAL, STOCK, CRYPTO
}