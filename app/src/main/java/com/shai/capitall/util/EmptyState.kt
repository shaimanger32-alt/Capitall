package com.shai.capitall.util

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.shai.capitall.R

/**
 * הגדרת מצב ריק אחיד (view_empty_state) ממסך כלשהו: אייקון, כותרת, גוף,
 * וכפתור פעולה אופציונלי שמוביל את המשתמש לצעד הבא במקום להשאיר אותו במסך ריק.
 */
fun View.bindEmptyState(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    @DrawableRes iconRes: Int = R.drawable.ic_analytics_empty,
    @StringRes actionRes: Int? = null,
    onAction: (() -> Unit)? = null
) {
    findViewById<ImageView>(R.id.ivEmptyIcon).setImageResource(iconRes)
    findViewById<TextView>(R.id.tvEmptyTitle).setText(titleRes)
    findViewById<TextView>(R.id.tvEmptyBody).setText(bodyRes)

    val button = findViewById<MaterialButton>(R.id.btnEmptyAction)
    if (actionRes != null && onAction != null) {
        button.setText(actionRes)
        button.visibility = View.VISIBLE
        button.setOnClickListener { onAction() }
    } else {
        button.visibility = View.GONE
    }
}
