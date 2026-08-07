package com.shai.capitall.util

/**
 * לוגיקה טהורה ליישור שתי סדרות זמן לציר-זמן משותף (איחוד התאריכים) עם השלמה קדימה.
 * מופרד מ-UI כדי שיהיה ניתן לבדיקה (ראה SeriesAlignerTest) — גרף הדשבורד מתרגם את התוצאה
 * לנקודות MPAndroidChart.
 */
object SeriesAligner {

    /** איחוד ממוין וייחודי של כל התאריכים מכל הסדרות. */
    fun unionTimestamps(vararg series: List<Long>): List<Long> =
        series.flatMap { it }.distinct().sorted()

    /**
     * השלמה קדימה של סדרה (זמן→ערך) על ציר-זמן נתון: לכל אינדקס בציר מוחזר הערך האחרון
     * שידוע עד אותו תאריך. אינדקסים שלפני הנקודה הראשונה של הסדרה נשמטים (אין ערך ידוע).
     */
    fun forwardFill(series: List<Pair<Long, Float>>, axis: List<Long>): Map<Int, Float> {
        val sorted = series.sortedBy { it.first }
        val out = LinkedHashMap<Int, Float>()
        var last: Float? = null
        var i = 0
        for ((index, ts) in axis.withIndex()) {
            while (i < sorted.size && sorted[i].first <= ts) { last = sorted[i].second; i++ }
            last?.let { out[index] = it }
        }
        return out
    }
}
