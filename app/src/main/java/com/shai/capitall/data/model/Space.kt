package com.shai.capitall.data.model

import com.google.firebase.firestore.Exclude

/**
 * תיק משותף — פנקס הוצאות/הכנסות שמשותף לכמה משתמשים (בני זוג, שותפים לדירה).
 *
 * מזהה המסמך **הוא קוד ההצטרפות**: קוד קצר שאפשר להקריא בטלפון. כך ההצטרפות היא
 * קריאה ישירה של מסמך לפי מזהה ולא שאילתה — מה שמאפשר חוק אבטחה פשוט ובטוח
 * ("מותר לקרוא רק אם אתה יודע את הקוד המדויק", בלי יכולת לרשום את כל התיקים).
 *
 * [memberNames] מוחזק בכפילות בכוונה: בלעדיו מסך המאזן היה מציג מזהי משתמש גולמיים,
 * וקריאת פרופיל לכל חבר בכל רינדור היא בזבוז מול נתון שמשתנה כמעט לעולם לא.
 */
data class Space(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * קוד ההצטרפות שמוצג למשתמש — זהה למזהה המסמך.
     * ‎@get:Exclude כדי ש-Firestore לא יכתוב אותו כשדה כפול בתוך המסמך.
     */
    @get:Exclude
    val inviteCode: String get() = id

    @Exclude
    fun nameOf(userId: String): String = memberNames[userId] ?: userId.take(6)

    companion object {
        /** ללא 0/O/1/I — תווים שמתבלבלים כשמקריאים קוד בטלפון. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 6

        fun generateCode(): String =
            (1..CODE_LENGTH).map { ALPHABET.random() }.joinToString("")

        fun normalizeCode(raw: String): String =
            raw.trim().uppercase().filter { it in ALPHABET }
    }
}
