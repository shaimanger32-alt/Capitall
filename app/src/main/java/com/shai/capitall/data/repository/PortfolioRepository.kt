package com.shai.capitall.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shai.capitall.data.model.Asset
import com.shai.capitall.data.model.Liability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "PortfolioRepository"

class PortfolioRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val assetsCollection = firestore.collection("assets")
    private val liabilitiesCollection = firestore.collection("liabilities")

    fun observeAssets(userId: String): Flow<List<Asset>> = callbackFlow {
        val registration: ListenerRegistration = assetsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeAssets failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val assets = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Asset::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(assets)
            }
        awaitClose { registration.remove() }
    }

    // גרסאות שמפיצות שגיאות (Result) במקום לבלוע אותן — לשימוש מסכים שמציגים מצב שגיאה אמיתי
    fun observeAssetsResult(userId: String): Flow<Result<List<Asset>>> = callbackFlow {
        val registration: ListenerRegistration = assetsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeAssetsResult failed", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val assets = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Asset::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(Result.success(assets))
            }
        awaitClose { registration.remove() }
    }

    fun observeLiabilitiesResult(userId: String): Flow<Result<List<Liability>>> = callbackFlow {
        val registration: ListenerRegistration = liabilitiesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeLiabilitiesResult failed", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val liabilities = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Liability::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(Result.success(liabilities))
            }
        awaitClose { registration.remove() }
    }

    fun observeLiabilities(userId: String): Flow<List<Liability>> = callbackFlow {
        val registration: ListenerRegistration = liabilitiesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeLiabilities failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val liabilities = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Liability::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(liabilities)
            }
        awaitClose { registration.remove() }
    }

    // עקומת השווי הנקי מחושבת ישירות מהנכסים/ההתחייבויות (ראה DashboardViewModel.buildValueSeries),
    // ולכן אין צורך לתחזק אוסף snapshots נפרד — הכתיבה אליו הוסרה.
    suspend fun addAsset(asset: Asset) {
        assetsCollection.add(asset).await()
    }

    suspend fun addLiability(liability: Liability) {
        liabilitiesCollection.add(liability).await()
    }

    suspend fun deleteAsset(assetId: String) {
        assetsCollection.document(assetId).delete().await()
    }

    suspend fun deleteLiability(liabilityId: String) {
        liabilitiesCollection.document(liabilityId).delete().await()
    }
}