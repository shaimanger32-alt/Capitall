package com.shai.capitall.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shai.capitall.data.model.CategoryScope
import com.shai.capitall.util.CategoryCatalog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "CategoryRepository"

class CategoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val categoriesCollection = firestore.collection("categories")

    // קטגוריות בסיס קבועות שקיימות תמיד (מתוך ה-catalog המובנה), בנוסף לקטגוריות מותאמות אישית מ-Firestore
    val defaultCategories =
        (CategoryCatalog.forScope(CategoryScope.TRANSACTION_EXPENSE) + CategoryCatalog.forScope(CategoryScope.TRANSACTION_INCOME))
            .map { it.key }

    fun observeCategories(userId: String): Flow<List<String>> = callbackFlow {
        val registration: ListenerRegistration = categoriesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeCategories failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val custom = snapshot?.documents?.mapNotNull { it.getString("name") } ?: emptyList()
                trySend((defaultCategories + custom).distinct())
            }
        awaitClose { registration.remove() }
    }

    suspend fun getCategoriesOnce(userId: String): List<String> {
        val snapshot = categoriesCollection.whereEqualTo("userId", userId).get().await()
        val custom = snapshot.documents.mapNotNull { it.getString("name") }
        return (defaultCategories + custom).distinct()
    }

    suspend fun addCategory(userId: String, name: String) {
        categoriesCollection.add(mapOf("userId" to userId, "name" to name)).await()
    }
}