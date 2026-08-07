package com.shai.capitall.util

import com.shai.capitall.data.model.Asset
import com.shai.capitall.data.model.Liability
import kotlin.math.pow

/**
 * הערכת שווי אוטומטית: מחשב את השווי הנוכחי המשוער של נכס/התחייבות לפי הזמן שחלף
 * מתאריך הרכישה כפול שיעור שינוי שנתי ממוצע לפי קטגוריה (מקורות ארוכי-טווח מקובלים).
 * מניות מסוג STOCK מקבלות מחיר שוק חי בנפרד ולא נכנסות לכאן.
 */
object AssetValuation {

    private const val MILLIS_PER_YEAR = 365.25 * 24 * 60 * 60 * 1000

    // שיעור שינוי שנתי לנכסים (חיובי = עליית ערך, שלילי = פחת)
    fun assetAnnualRate(categoryKey: String): Double = when (categoryKey) {
        "real_estate" -> 0.035          // עליית ערך נדל"ן ארוכת-טווח
        "vehicle" -> -0.15              // פחת רכב ממוצע
        "pension_retirement" -> 0.05    // תשואת קרנות פנסיה/גמל
        "bonds" -> 0.03                 // תשואת אג"ח
        "stocks_investments" -> 0.07    // ממוצע שוק ארוך-טווח (לרשומות ידניות בלבד)
        "cash_savings" -> 0.015         // ריבית ממוצעת על חיסכון
        else -> 0.0                     // קריפטו / מט"ח / עסק / אחר — אין ממוצע אמין
    }

    // ריבית שנתית ממוצעת (משמשת כריבית ההלוואה בחישוב אמורטיזציה)
    fun liabilityAnnualRate(categoryKey: String): Double = when (categoryKey) {
        "mortgage" -> 0.04
        "loan" -> 0.07
        "student_loan" -> 0.045
        "credit_card" -> 0.15
        "credit_line" -> 0.10
        else -> 0.0
    }

    // הלוואות סילוקין שהיתרה שלהן יורדת עם התשלומים (בניגוד לחוב מתגלגל כמו אשראי)
    private val AMORTIZING_LIABILITIES = setOf("mortgage", "loan", "student_loan")

    fun isAmortizing(categoryKey: String): Boolean = categoryKey in AMORTIZING_LIABILITIES

    // השיעור בפועל: ערך מותאם אישית שהמשתמש הזין, ואם אין — ממוצע הקטגוריה כברירת מחדל
    fun effectiveAssetRate(asset: Asset): Double =
        asset.annualRate ?: assetAnnualRate(asset.category)

    fun effectiveLiabilityRate(liability: Liability): Double =
        liability.annualRate ?: liabilityAnnualRate(liability.category)

    private fun yearsElapsed(createdAt: Long, now: Long): Double =
        (now - createdAt).coerceAtLeast(0L) / MILLIS_PER_YEAR

    private fun project(baseValue: Double, rate: Double, createdAt: Long, now: Long): Double {
        if (rate == 0.0) return baseValue
        return baseValue * (1 + rate).pow(yearsElapsed(createdAt, now))
    }

    /**
     * שווי הנכס בשקלים: קודם השערוך לפי הזמן שחלף, ואז המרה מהמטבע שבו הוא נקוב.
     * נכסי מט"ח נקובים ב-USD/EUR; לכל שאר הנכסים `currency` הוא null (=שקל) וההמרה שקופה.
     * זו נקודת המעבר היחידה — כל מסכי הסכימה קוראים לכאן ולכן מקבלים שקלים.
     */
    fun projectedAssetValue(asset: Asset, now: Long = System.currentTimeMillis()): Double {
        val grown = project(asset.value, effectiveAssetRate(asset), asset.createdAt, now)
        return CurrencyConverter.toIls(grown, asset.currency)
    }

    // חישוב יתרת חוב ריאלי: הלוואות סילוקין (משכנתא/הלוואה) יורדות לפי לוח סילוקין;
    // חוב מתגלגל/אחר (אשראי) נשאר קבוע (אין מספיק נתונים למודל אמין).
    fun projectedLiabilityValue(liability: Liability, now: Long = System.currentTimeMillis()): Double {
        if (!isAmortizing(liability.category)) return liability.value
        val termYears = liability.termYears ?: return liability.value
        if (termYears <= 0) return liability.value

        val principal = liability.value
        if (principal <= 0.0) return principal

        val n = termYears * 12.0                                   // סך התשלומים
        val t = (yearsElapsed(liability.createdAt, now) * 12.0).coerceAtMost(n) // חודשים שחלפו
        val i = effectiveLiabilityRate(liability) / 12.0           // ריבית חודשית

        val balance = if (i == 0.0) {
            principal * (1.0 - t / n)                              // פריסה לינארית ללא ריבית
        } else {
            val powT = (1 + i).pow(t)
            val monthlyPayment = principal * i / (1 - (1 + i).pow(-n))
            principal * powT - monthlyPayment * (powT - 1) / i
        }
        return balance.coerceIn(0.0, principal)
    }

    /**
     * אחוז השינוי מאז הרכישה, או null אם אין שינוי (אין מה להציג).
     * מחושב מהשיעור בלבד ולא מיחס הערכים — כך הוא נשאר נכון גם לנכס מט"ח,
     * שבו projectedAssetValue כולל המרה למטבע אחר ויחס כזה היה משקף את שער החליפין.
     */
    fun assetChangePercent(asset: Asset, now: Long = System.currentTimeMillis()): Double? {
        val rate = effectiveAssetRate(asset)
        if (rate == 0.0 || asset.value == 0.0) return null
        return ((1 + rate).pow(yearsElapsed(asset.createdAt, now)) - 1.0) * 100.0
    }

    fun liabilityChangePercent(liability: Liability, now: Long = System.currentTimeMillis()): Double? {
        if (liability.value == 0.0) return null
        val current = projectedLiabilityValue(liability, now)
        if (current == liability.value) return null
        return (current / liability.value - 1.0) * 100.0
    }
}
