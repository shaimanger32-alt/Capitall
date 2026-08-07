package com.shai.capitall.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.shai.capitall.data.model.Space
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "SpaceRepository"

/** תוצאת ניסיון הצטרפות לתיק — כדי שהמסך יציג הודעה מדויקת ולא "נכשל". */
sealed interface JoinResult {
    data class Success(val space: Space) : JoinResult
    data object NotFound : JoinResult
    data class AlreadyMember(val space: Space) : JoinResult
    data object PermissionDenied : JoinResult
    data object Failed : JoinResult
}

/** תוצאת ניסיון יצירת תיק. */
sealed interface CreateResult {
    data class Success(val space: Space) : CreateResult

    /**
     * השרת דחה את הפעולה. כמעט תמיד הסיבה היא שחוקי האבטחה של אוסף `spaces`
     * לא נפרסו — ולכן זו הודעה נפרדת ולא "נכשל" גנרי, שהיה שולח לחפש במקום הלא נכון.
     */
    data object PermissionDenied : CreateResult
    data object Failed : CreateResult
}

class SpaceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val spacesCollection = firestore.collection("spaces")

    /** התיקים שהמשתמש חבר בהם, בזמן אמת. */
    fun observeSpaces(userId: String): Flow<List<Space>> = callbackFlow {
        val registration: ListenerRegistration = spacesCollection
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeSpaces failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Space::class.java)?.copy(id = doc.id)
                }?.sortedBy { it.createdAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getSpace(spaceId: String): Space? {
        val doc = spacesCollection.document(spaceId).get().await()
        return if (doc.exists()) doc.toObject(Space::class.java)?.copy(id = doc.id) else null
    }

    /**
     * יוצר תיק חדש. מזהה המסמך הוא קוד ההצטרפות, ולכן נדרשת בדיקת התנגשות —
     * הקוד קצר בכוונה (6 תווים) כדי שיהיה אפשר להקריא אותו, וזה מחיר הקיצור.
     */
    suspend fun createSpace(name: String, ownerId: String, ownerName: String): CreateResult {
        repeat(CODE_ATTEMPTS) {
            val code = Space.generateCode()
            val reference = spacesCollection.document(code)

            val space = Space(
                id = code,
                name = name.trim(),
                ownerId = ownerId,
                memberIds = listOf(ownerId),
                memberNames = mapOf(ownerId to ownerName)
            )

            try {
                if (reference.get().await().exists()) return@repeat // התנגשות קוד — מגרילים שוב
                reference.set(space).await()
                return CreateResult.Success(space)
            } catch (e: Exception) {
                Log.e(TAG, "createSpace failed", e)
                return if (isPermissionDenied(e)) CreateResult.PermissionDenied else CreateResult.Failed
            }
        }
        Log.e(TAG, "could not allocate a free invite code after $CODE_ATTEMPTS attempts")
        return CreateResult.Failed
    }

    /** מזהה דחייה של חוקי האבטחה, להבדיל מתקלת רשת. */
    private fun isPermissionDenied(e: Exception): Boolean =
        (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    /**
     * הצטרפות לפי קוד. משתמש ב-[FieldValue.arrayUnion] ולא בכתיבת רשימה שלמה —
     * כך שני משתמשים שמצטרפים בו-זמנית לא דורסים זה את זה.
     */
    suspend fun joinSpace(rawCode: String, userId: String, userName: String): JoinResult {
        val code = Space.normalizeCode(rawCode)
        if (code.length != Space.CODE_LENGTH) return JoinResult.NotFound

        return try {
            val space = getSpace(code) ?: return JoinResult.NotFound
            if (userId in space.memberIds) return JoinResult.AlreadyMember(space)

            spacesCollection.document(code).update(
                mapOf(
                    "memberIds" to FieldValue.arrayUnion(userId),
                    "memberNames.$userId" to userName
                )
            ).await()
            JoinResult.Success(space.copy(memberIds = space.memberIds + userId))
        } catch (e: Exception) {
            Log.e(TAG, "joinSpace failed", e)
            if (isPermissionDenied(e)) JoinResult.PermissionDenied else JoinResult.Failed
        }
    }

    /**
     * עזיבת תיק. העסקאות שהמשתמש יצר **נשארות** בתיק — מחיקתן הייתה משנה
     * למפרע את המאזן של כל השאר. השם נשאר ב-[Space.memberNames] כדי שהיסטוריה
     * ישנה תמשיך להציג שם ולא מזהה.
     */
    suspend fun leaveSpace(spaceId: String, userId: String): Boolean = try {
        spacesCollection.document(spaceId)
            .update("memberIds", FieldValue.arrayRemove(userId))
            .await()
        true
    } catch (e: Exception) {
        Log.e(TAG, "leaveSpace failed", e)
        false
    }

    /** מחיקת תיק — לבעלים בלבד. העסקאות עצמן נמחקות בנפרד ע"י ה-ViewModel. */
    suspend fun deleteSpace(spaceId: String): Boolean = try {
        spacesCollection.document(spaceId).delete().await()
        true
    } catch (e: Exception) {
        Log.e(TAG, "deleteSpace failed", e)
        false
    }

    private companion object {
        const val CODE_ATTEMPTS = 5
    }
}
