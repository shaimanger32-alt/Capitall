package com.shai.capitall.util.csv

import com.shai.capitall.data.model.Transaction
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * מזהה שורות יבוא שכבר קיימות בחשבון — כדי שיבוא חוזר של אותו קובץ, או יבוא של קבצים
 * עם חפיפה בתאריכים, לא יכפיל עסקאות.
 *
 * החתימה היא בית עסק + סכום + *יום* (ולא חותמת זמן מדויקת), כי קבצי בנק מציינים תאריך
 * בלבד בעוד שעסקה שהוזנה ידנית נושאת שעה.
 *
 * ההשוואה סופרת ריבויים: אם המשתמש באמת קנה פעמיים באותו סכום באותו יום, ובחשבון קיימת
 * רק אחת מהן — רק אחת תסומן ככפולה והשנייה תיובא. שימוש בקבוצה (Set) היה מבליע כאן עסקה.
 *
 * לוגיקה טהורה (ללא Android/Firebase) — ראה ImportDeduplicatorTest.
 */
object ImportDeduplicator {

    private fun dayKey(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
    }

    /** חתימה יציבה לזיהוי עסקה חוזרת. הסכום מעוגל לאגורות כדי לנטרל שגיאות float. */
    fun signature(merchant: String, amount: Double, timestamp: Long): String {
        val normalizedMerchant = BankStatementParser.normalize(merchant)
        val cents = (abs(amount) * 100).roundToLong()
        val sign = if (amount < 0) "-" else "+"
        return "$normalizedMerchant|$sign$cents|${dayKey(timestamp)}"
    }

    /**
     * מחזיר, לכל שורה ב-[rows] לפי סדרה, האם היא כבר קיימת ב-[existing].
     */
    fun findDuplicates(rows: List<ImportRow>, existing: List<Transaction>): List<Boolean> {
        val remaining = HashMap<String, Int>()
        existing.forEach { transaction ->
            val key = signature(transaction.merchant, transaction.amount, transaction.timestamp)
            remaining[key] = (remaining[key] ?: 0) + 1
        }

        return rows.map { row ->
            val key = signature(row.merchant, row.amount, row.timestamp)
            val count = remaining[key] ?: 0
            if (count > 0) {
                remaining[key] = count - 1
                true
            } else {
                false
            }
        }
    }
}
