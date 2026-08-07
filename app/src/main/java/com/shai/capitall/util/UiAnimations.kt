package com.shai.capitall.util

import android.animation.AnimatorInflater
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R

/**
 * שכבת התנועה של Capitall — מיקרו-אינטראקציות שמעניקות לאפליקציה תחושת חיים:
 * ספירת מספרים עולה לערכים פיננסיים, כניסה מדורגת לרשימות, ופעימה למצבי טעינה.
 */

/**
 * מציג ערך כספי עם ספירה מונפשת מהערך הקודם אל החדש (count-up).
 * הערך הקודם נשמר על ה-View, כך שרענון חוזר מנפיש רק את ההפרש ולא קופץ מאפס.
 */
fun TextView.setValueAnimated(
    target: Double,
    durationMs: Long = 700L,
    format: (Double) -> String
) {
    val previous = getTag(R.id.tag_animated_value) as? Double
    setTag(R.id.tag_animated_value, target)
    (getTag(R.id.tag_value_animator) as? ValueAnimator)?.cancel()

    // הצגה ראשונה או ערך זהה — בלי אנימציה מיותרת
    if (previous == null || previous == target) {
        text = format(target)
        return
    }

    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        interpolator = DecelerateInterpolator()
        addUpdateListener { anim ->
            val fraction = anim.animatedFraction
            text = format(previous + (target - previous) * fraction)
        }
    }
    setTag(R.id.tag_value_animator, animator)
    animator.start()
}

/** מפעיל כניסה מדורגת (stagger) לפריטי הרשימה — נקרא אחרי הזנת נתונים חדשים. */
fun RecyclerView.playRiseAnimation() {
    layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_rise)
    scheduleLayoutAnimation()
}

/** פעימת טעינה למצייני מקום (skeleton). */
fun View.startPulse() {
    AnimatorInflater.loadAnimator(context, R.animator.pulse).apply {
        setTarget(this@startPulse)
        start()
    }
}
