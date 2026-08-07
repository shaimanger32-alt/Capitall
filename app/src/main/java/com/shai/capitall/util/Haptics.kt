package com.shai.capitall.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * פידבק מישושי אחיד לכל האפליקציה — חלק משפת האינטראקציה (פילים + לחיצה חיה).
 * עובד דרך HapticFeedbackConstants ולכן לא דורש הרשאת VIBRATE.
 */

/** רטט קצרצר לבחירה/מעבר (צ'יפ, טווח, סגמנט, עין). */
fun View.hapticTap() {
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

/** רטט אישור לפעולה משמעותית (שמירה/הוספה). */
fun View.hapticConfirm() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
