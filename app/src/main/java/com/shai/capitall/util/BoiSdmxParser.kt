package com.shai.capitall.util

import com.shai.capitall.data.remote.BoiSdmxResponse
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * פענוח תשובת SDMX-JSON של בנק ישראל לזוגות (תאריך, שער).
 *
 * ב-SDMX התצפיות ממופות לפי אינדקס ולא לפי תאריך; רשימת התאריכים יושבת בנפרד
 * תחת structure.dimensions.observation. הפרסר מצליב ביניהם.
 *
 * לוגיקה טהורה (ללא Android/רשת) — ולכן מכוסה בבדיקות יחידה.
 */
object BoiSdmxParser {

    /** נקודת שער יומית: תאריך במילישניות UTC + השער (כמה שקלים ליחידת מטבע). */
    data class RatePoint(val timestamp: Long, val rate: Double)

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * מחזיר את כל נקודות השער בתשובה, ממוינות כרונולוגית.
     * תצפיות ללא תאריך תקין או ללא ערך מספרי חיובי — מושמטות.
     */
    fun parseSeries(response: BoiSdmxResponse?): List<RatePoint> {
        val data = response?.data ?: return emptyList()

        // רשימת התאריכים לפי סדר האינדקס (תומך גם ב-structures מערך וגם ב-structure יחיד)
        val structure = data.structures?.firstOrNull() ?: data.structure
        val periods = structure?.dimensions?.observation
            ?.firstOrNull { it.id.equals("TIME_PERIOD", ignoreCase = true) || it.values != null }
            ?.values
            ?.mapNotNull { it.id }
            ?: return emptyList()

        val dataSet = data.dataSets?.firstOrNull() ?: return emptyList()
        val observations = dataSet.series?.values?.firstOrNull()?.observations
            ?: dataSet.observations
            ?: return emptyList()

        val format = dateFormat()
        return observations.mapNotNull { (indexKey, values) ->
            val index = indexKey.toIntOrNull() ?: return@mapNotNull null
            val dateText = periods.getOrNull(index) ?: return@mapNotNull null
            val rate = (values.firstOrNull() as? Number)?.toDouble()
                ?: values.firstOrNull()?.toString()?.toDoubleOrNull()
                ?: return@mapNotNull null
            if (rate <= 0.0) return@mapNotNull null
            val millis = runCatching { format.parse(dateText)?.time }.getOrNull() ?: return@mapNotNull null
            RatePoint(millis, rate)
        }.sortedBy { it.timestamp }
    }

    /** השער העדכני ביותר בתשובה, או null אם אין תצפית תקינה. */
    fun parseLatestRate(response: BoiSdmxResponse?): Double? =
        parseSeries(response).lastOrNull()?.rate
}
