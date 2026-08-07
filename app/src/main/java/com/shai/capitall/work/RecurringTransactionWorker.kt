package com.shai.capitall.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.di.ServiceLocator
import com.shai.capitall.util.RecurringGenerator

/**
 * עבודת רקע יומית שמייצרת את העסקאות החוזרות (isRecurring) שחסרות לחודש הנוכחי.
 * הלוגיקה עצמה טהורה ב-[RecurringGenerator]; כאן רק משיכת הנתונים והכתיבה.
 */
class RecurringTransactionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val repository = ServiceLocator.transactionRepository
        return try {
            val existing = repository.getTransactionsOnce(userId)
            val due = RecurringGenerator.dueOccurrences(existing)
            due.forEach { repository.addTransaction(it) }
            if (due.isNotEmpty()) Log.i(TAG, "created ${due.size} recurring transactions")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "recurring generation failed", e)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "recurring_transactions"
        private const val TAG = "RecurringWorker"
    }
}
