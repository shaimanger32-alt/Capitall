package com.shai.capitall

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.work.RecurringTransactionWorker
import java.util.concurrent.TimeUnit

/**
 * טוען העדפות גלובליות פעם אחת בעליית התהליך — לפני כל Activity.
 * כרגע: מטבע התצוגה (הגדרות ← מטבע תצוגה), ותזמון עבודת העסקאות החוזרות.
 */
class CapitallApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        CurrencyConverter.setDisplayCurrency(prefs.getString(KEY_DISPLAY_CURRENCY, "ILS") ?: "ILS")
        // טוען את משאבי הגופנים של PDFBox — נדרש לפני קריאת PDF ביבוא דפי חשבון
        PDFBoxResourceLoader.init(applicationContext)
        scheduleRecurringTransactions()
    }

    // עבודה יומית שמייצרת אוטומטית עסקאות חוזרות (isRecurring) לחודש הנוכחי
    private fun scheduleRecurringTransactions() {
        val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RecurringTransactionWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val PREFS_NAME = "capitall_prefs"
        const val KEY_DISPLAY_CURRENCY = "display_currency"
        const val KEY_BALANCE_HIDDEN = "balance_hidden"
    }
}
