package com.shai.capitall.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    // Capitall מעוצב במצב בהיר קבוע (Light / Robinhood-style). אין מתג בהיר/כהה — זו האופציה היחידה.
    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
