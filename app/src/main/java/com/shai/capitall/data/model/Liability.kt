package com.shai.capitall.data.model

data class Liability(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "",
    val value: Double = 0.0,
    val recurringPaymentAmount: Double? = null, // הערכת תשלום חודשי חוזר (למשל החזר משכנתא), רלוונטי לפי קטגוריה
    val annualRate: Double? = null, // ריבית שנתית מותאמת אישית (null = ממוצע הקטגוריה כברירת מחדל)
    val termYears: Int? = null, // תקופת ההלוואה בשנים (למשכנתא/הלוואה — נדרש לחישוב אמורטיזציה)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)