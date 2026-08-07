package com.shai.capitall.util

import android.content.Context
import android.util.Log
import com.shai.capitall.CapitallApp
import com.shai.capitall.data.repository.TransactionRepository

/**
 * מיגרציה חד-פעמית של מפתחות קטגוריות עסקה ישנים, שאוחדו לקטגוריות הכלליות החדשות,
 * כדי שעסקאות היסטוריות ימשיכו להופיע תחת הקטגוריה הנכונה (ולא כמפתח גולמי אפור).
 * רצה פעם אחת בלבד (מסומן ב-SharedPreferences), ולא חוסמת את המשתמש אם נכשלה.
 */
object CategoryMigration {

    // מפתח ישן → מפתח חדש. ראה [CategoryCatalog] לרשימת הקטגוריות המאוחדת.
    val LEGACY_MAP: Map<String, String> = mapOf(
        "Restaurants" to "food",
        "Delivery" to "food",
        "Supermarket" to "food",
        "Utilities" to "Housing"
    )

    private const val PREF_KEY = "category_migration_v1_done"
    private const val TAG = "CategoryMigration"

    /** מריץ את המיגרציה פעם אחת עבור המשתמש הנתון (no-op אם כבר רצה או נכשל בעבר בשקט). */
    suspend fun runOnce(
        context: Context,
        userId: String,
        repository: TransactionRepository = com.shai.capitall.di.ServiceLocator.transactionRepository
    ) {
        val prefs = context.getSharedPreferences(CapitallApp.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_KEY, false)) return
        runCatching { repository.migrateCategoryKeys(userId, LEGACY_MAP) }
            .onSuccess { count ->
                prefs.edit().putBoolean(PREF_KEY, true).apply()
                if (count > 0) Log.i(TAG, "migrated $count transactions to consolidated categories")
            }
            .onFailure { Log.e(TAG, "category migration failed (will retry next launch)", it) }
    }
}
