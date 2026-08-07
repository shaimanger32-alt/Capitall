package com.shai.capitall.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shai.capitall.data.model.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "TransactionRepository"

class TransactionRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val transactionsCollection = firestore.collection("transactions")

    /**
     * העסקאות **האישיות** של המשתמש — אלה שאינן שייכות לתיק משותף.
     *
     * הסינון נעשה בצד הלקוח ולא בשאילתה בכוונה: ב-Firestore מסמך שחסר בו השדה
     * אינו תואם ל-`whereEqualTo("spaceId", "")`, ולכן שאילתה כזו הייתה מסתירה את כל
     * העסקאות שנוצרו לפני הפיצ'ר. סינון בצד הלקוח מוסיף את הפיצ'ר **בלי מיגרציה**,
     * והמחיר זניח בהיקף של אפליקציה אישית.
     */
    fun observeTransactions(userId: String): Flow<List<Transaction>> =
        observePersonal(userId) { it.getOrDefault(emptyList()) }

    /** גרסה שמפיצה שגיאות (Result) — למסכים שמציגים מצב שגיאה אמיתי. */
    fun observeTransactionsResult(userId: String): Flow<Result<List<Transaction>>> =
        observePersonal(userId) { it }

    private fun <T> observePersonal(
        userId: String,
        wrap: (Result<List<Transaction>>) -> T
    ): Flow<T> = callbackFlow {
        val registration: ListenerRegistration = transactionsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeTransactions failed", error)
                    trySend(wrap(Result.failure(error)))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toObject(Transaction::class.java)?.copy(id = doc.id) }
                    ?.filter { !it.isShared }
                    ?: emptyList()
                trySend(wrap(Result.success(list)))
            }
        awaitClose { registration.remove() }
    }

    /**
     * העסקאות של תיק משותף — של **כל** החברים בו, בזמן אמת.
     * הגישה נשלטת בחוקי Firestore לפי חברות ב-[com.shai.capitall.data.model.Space].
     */
    fun observeSpaceTransactions(spaceId: String): Flow<Result<List<Transaction>>> = callbackFlow {
        val registration: ListenerRegistration = transactionsCollection
            .whereEqualTo("spaceId", spaceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeSpaceTransactions failed", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                trySend(Result.success(list))
            }
        awaitClose { registration.remove() }
    }

    /** מחיקת כל עסקאות התיק — נקרא כשהבעלים מוחק תיק משותף. */
    suspend fun deleteSpaceTransactions(spaceId: String): Int {
        val snap = transactionsCollection.whereEqualTo("spaceId", spaceId).get().await()
        if (snap.isEmpty) return 0
        var deleted = 0
        snap.documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
            deleted += chunk.size
        }
        return deleted
    }

    suspend fun addTransaction(transaction: Transaction) {
        transactionsCollection.add(transaction).await()
    }

    /**
     * כתיבת עסקאות מרובות בבת אחת (יבוא מקובץ בנק). מחולק ל-500 לכל commit — מגבלת
     * Firestore לפעולות ב-batch יחיד. מחזיר את מספר העסקאות שנכתבו.
     *
     * ה-batch הוא אטומי לכל נתח: אם commit נכשל, אף עסקה מאותו נתח לא נכתבת, וכך
     * לא נשארות עסקאות "חצי מיובאות" מאותו נתח.
     */
    suspend fun addTransactions(transactions: List<Transaction>): Int {
        if (transactions.isEmpty()) return 0
        var written = 0
        transactions.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { transaction ->
                batch.set(transactionsCollection.document(), transaction)
            }
            batch.commit().await()
            written += chunk.size
        }
        return written
    }

    /**
     * משיכה חד-פעמית של העסקאות האישיות (ללא listener) — לעבודות רקע וליבוא.
     * מוגבל לתיק האישי בכוונה: עסקאות חוזרות וזיהוי כפילויות ביבוא הם ענייני
     * המשתמש עצמו, ולא צריכים לגרור אליהם את הפנקסים המשותפים.
     */
    suspend fun getTransactionsOnce(userId: String): List<Transaction> {
        val snap = transactionsCollection.whereEqualTo("userId", userId).get().await()
        return snap.documents
            .mapNotNull { it.toObject(Transaction::class.java)?.copy(id = it.id) }
            .filter { !it.isShared }
    }

    suspend fun deleteTransaction(transactionId: String) {
        transactionsCollection.document(transactionId).delete().await()
    }

    suspend fun updateTransaction(transactionId: String, updates: Map<String, Any>) {
        transactionsCollection.document(transactionId).update(updates).await()
    }

    /**
     * ממפה מפתחות קטגוריה ישנים לחדשים בעסקאות קיימות (מיגרציה חד-פעמית).
     * לכל מפתח ישן: מושך את העסקאות של המשתמש עם אותה קטגוריה ומעדכן אותן בכתיבת batch
     * (מחולק ל-500 לכל commit — מגבלת Firestore). מחזיר את מספר העסקאות שעודכנו.
     */
    suspend fun migrateCategoryKeys(userId: String, mapping: Map<String, String>): Int {
        var migrated = 0
        for ((oldKey, newKey) in mapping) {
            val snap = transactionsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("category", oldKey)
                .get().await()
            if (snap.isEmpty) continue
            snap.documents.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.update(doc.reference, "category", newKey) }
                batch.commit().await()
                migrated += chunk.size
            }
        }
        return migrated
    }
}