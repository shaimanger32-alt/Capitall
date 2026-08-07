package com.shai.capitall.util

import com.shai.capitall.data.model.Transaction
import java.util.Calendar

/**
 * לוגיקה טהורה (ללא Firebase) לייצור עסקאות חוזרות: בהינתן העסקאות הקיימות והזמן הנוכחי,
 * מחזיר את העסקאות החדשות שיש ליצור לחודש הנוכחי — אחת לכל "תבנית" חוזרת (isRecurring)
 * שאין לה עדיין מופע בחודש הנוכחי. מזוהה לפי חתימת סוחר+קטגוריה+סכום כדי למנוע כפילויות.
 */
object RecurringGenerator {

    private fun signature(t: Transaction) = "${t.merchant}|${t.category}|${t.amount}"

    fun dueOccurrences(existing: List<Transaction>, now: Long = System.currentTimeMillis()): List<Transaction> {
        val recurring = existing.filter { it.isRecurring }
        if (recurring.isEmpty()) return emptyList()

        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val curYear = nowCal.get(Calendar.YEAR)
        val curMonth = nowCal.get(Calendar.MONTH)

        fun isCurrentMonth(ts: Long): Boolean {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            return c.get(Calendar.YEAR) == curYear && c.get(Calendar.MONTH) == curMonth
        }

        return recurring.groupBy { signature(it) }.mapNotNull { (_, group) ->
            // אם כבר קיים מופע החודש — לא מייצרים שוב (מונע כפילויות אם העבודה רצה כמה פעמים)
            if (group.any { isCurrentMonth(it.timestamp) }) return@mapNotNull null
            val template = group.maxByOrNull { it.timestamp } ?: return@mapNotNull null

            // אותו יום-בחודש כמו התבנית, מוצמד לאורך החודש הנוכחי (למשל 31 → 28/30 לפי החודש)
            val templateDay = Calendar.getInstance()
                .apply { timeInMillis = template.timestamp }
                .get(Calendar.DAY_OF_MONTH)
            val occurrence = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.DAY_OF_MONTH, templateDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            template.copy(id = "", timestamp = occurrence.timeInMillis)
        }
    }
}
