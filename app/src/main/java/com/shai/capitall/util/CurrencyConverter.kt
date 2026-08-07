package com.shai.capitall.util

import java.text.NumberFormat
import java.util.Locale

/**
 * שער חליפין USD→ILS משותף לכל האפליקציה.
 *
 * מניות אמריקאיות מנוהלות בדולר (מחיר קנייה ומחיר שוק חי), אבל כל התיק והשווי הנקי
 * מוצגים בשקל. לכן ערכי המניות מומרים לשקל לפי השער הנוכחי בכל נקודה שבה הם נכנסים
 * לסכום שקלי — וכך השווי הנקי מושפע גם מתנודות שער הדולר/שקל.
 *
 * השער נשמר בזיכרון (cache) ומתעדכן ע"י [com.shai.capitall.data.repository.StockRepository]
 * בכל טעינת מחירים, כדי שגם מסכים שאינם מושכים מחירים חיים יוכלו להמיר לפי שער עדכני.
 */
object CurrencyConverter {

    /** שערי ברירת מחדל שמשמשים עד שנטען שער חי (או אם הטעינה מהרשת נכשלה). */
    const val DEFAULT_USD_ILS = 3.7
    const val DEFAULT_EUR_ILS = 4.0

    /** קודי המטבע הנתמכים לנכסי מט"ח (הראשון = ברירת המחדל בבורר). */
    val SUPPORTED_CURRENCIES = listOf("USD", "EUR", "ILS")

    @Volatile
    var usdToIls: Double = DEFAULT_USD_ILS
        private set

    @Volatile
    var eurToIls: Double = DEFAULT_EUR_ILS
        private set

    /** מעדכן את שער הדולר (מתעלם מערכים לא-תקינים כדי לא לאפס את ה-cache). */
    fun updateRate(rate: Double) {
        if (rate > 0) usdToIls = rate
    }

    /** מעדכן את שער האירו (מתעלם מערכים לא-תקינים כדי לא לאפס את ה-cache). */
    fun updateEurRate(rate: Double) {
        if (rate > 0) eurToIls = rate
    }

    /** ממיר סכום בדולר לשקל לפי השער הנוכחי. */
    fun usdToIls(amountUsd: Double): Double = amountUsd * usdToIls

    /**
     * שער ההמרה לשקל עבור קוד מטבע. שקל (או null/ריק — נכסים ישנים ללא שדה מטבע) = 1.0,
     * ולכן נכס שאינו מט"ח עובר דרך הפונקציה הזו בלי להשתנות.
     */
    fun rateFor(currency: String?): Double = when (currency?.uppercase(Locale.US)) {
        "USD" -> usdToIls
        "EUR" -> eurToIls
        else -> 1.0
    }

    /** ממיר סכום מהמטבע הנתון לשקל. */
    fun toIls(amount: Double, currency: String?): Double = amount * rateFor(currency)

    /**
     * מטבע התצוגה של האפליקציה (העדפת משתמש מדף ההגדרות). הסכומים מנוהלים תמיד בשקל;
     * כשנבחר USD/EUR — [ilsFormatter] ממיר לשער הנוכחי בזמן הפורמט, וכל המסכים
     * מקבלים את זה בחינם כי כולם עוברים דרכו (נקודת אמת יחידה).
     */
    @Volatile
    var displayCurrency: String = "ILS"
        private set

    fun setDisplayCurrency(currency: String) {
        displayCurrency = if (currency.uppercase(Locale.US) in SUPPORTED_CURRENCIES) {
            currency.uppercase(Locale.US)
        } else {
            "ILS"
        }
    }

    /** עוטף פורמטר יעד וממיר את הסכום (הנקוב בשקל) למטבע התצוגה לפני הפורמט. */
    private class ConvertingFormat(
        private val target: NumberFormat,
        private val ilsPerUnit: Double
    ) : NumberFormat() {
        override fun format(number: Double, toAppendTo: StringBuffer, pos: java.text.FieldPosition): StringBuffer =
            target.format(number / ilsPerUnit, toAppendTo, pos)

        override fun format(number: Long, toAppendTo: StringBuffer, pos: java.text.FieldPosition): StringBuffer =
            target.format(number / ilsPerUnit, toAppendTo, pos)

        override fun parse(source: String, parsePosition: java.text.ParsePosition): Number? =
            target.parse(source, parsePosition)
    }

    /**
     * מנפיק פורמטר לסכומים הנקובים בשקל — נקודת אמת יחידה ל-Locale/מטבע בכל האפליקציה.
     * כשמטבע התצוגה אינו שקל, הפורמטר גם ממיר לפי השער הנוכחי.
     */
    fun ilsFormatter(): NumberFormat {
        val ils = NumberFormat.getCurrencyInstance(Locale("iw", "IL"))
        return when (displayCurrency) {
            "USD" -> ConvertingFormat(usdFormatter(), usdToIls)
            "EUR" -> ConvertingFormat(NumberFormat.getCurrencyInstance(Locale.GERMANY), eurToIls)
            else -> ils
        }
    }

    /**
     * פורמטר קומפקטי — אותו מטבע תצוגה, בלי אגורות. לווידג'טים צרים שבהם שתי הספרות
     * שאחרי הנקודה עולות יותר ברוחב ממה שהן תורמות בדיוק.
     *
     * נבנה במקביל ל-[ilsFormatter] (ולא כ"עטיפה" עליו) כי [ConvertingFormat] מאציל
     * לפורמטר היעד — קיצוץ הספרות חייב לקרות על היעד עצמו, אחרת הוא לא ישפיע כשמטבע
     * התצוגה אינו שקל.
     */
    fun ilsFormatterCompact(): NumberFormat = when (displayCurrency) {
        "USD" -> ConvertingFormat(usdFormatter().withoutDecimals(), usdToIls)
        "EUR" -> ConvertingFormat(
            NumberFormat.getCurrencyInstance(Locale.GERMANY).withoutDecimals(), eurToIls
        )
        else -> NumberFormat.getCurrencyInstance(Locale("iw", "IL")).withoutDecimals()
    }

    private fun NumberFormat.withoutDecimals(): NumberFormat = apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    /** מנפיק פורמטר מטבע דולרי (למסכי המניות המנוהלים בדולר). */
    fun usdFormatter(): NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    fun formatIls(amount: Double): String = ilsFormatter().format(amount)

    fun formatUsd(amount: Double): String = usdFormatter().format(amount)
}
