package com.shai.capitall.data.model

import com.google.firebase.firestore.Exclude

data class Transaction(
    val id: String = "",
    val userId: String = "",
    val merchant: String = "",
    val category: String = "",
    val amount: Double = 0.0,          // חיובי = הכנסה, שלילי = הוצאה
    val timestamp: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Card",
    val notes: String = "",
    val isRecurring: Boolean = false,

    /**
     * התיק שאליו העסקה שייכת. מחרוזת ריקה = התיק האישי (פרטי למשתמש בלבד).
     * ערך אחר = מזהה [Space] משותף, ואז העסקה גלויה לכל חברי התיק.
     *
     * ברירת המחדל הריקה היא מה שמאפשר להוסיף את הפיצ'ר בלי מיגרציה: מסמכים ישנים
     * שאין בהם את השדה מפוענחים ל-"" ולכן ממשיכים להיחשב אישיים.
     */
    val spaceId: String = "",

    /**
     * מי מחברי התיק שילם בפועל. ריק = היוצר ([userId]).
     * זהו הנתון שמאפשר לחשב מי חייב למי — בלעדיו תיק משותף הוא רק רשימה.
     */
    val paidBy: String = ""
) {
    /**
     * מזהה המשלם בפועל, עם נפילה ליוצר העסקה.
     *
     * ‎@get:Exclude חיוני כאן: Firestore מסריאל גם getters מחושבים, ובלעדיו כל עסקה
     * הייתה נכתבת עם שדה מיותר שנגזר משדות אחרים — ומי שיקרא את המסמך עלול לחשוב
     * שהוא מקור האמת.
     */
    @get:Exclude
    val payerId: String get() = paidBy.ifBlank { userId }

    @get:Exclude
    val isShared: Boolean get() = spaceId.isNotBlank()
}