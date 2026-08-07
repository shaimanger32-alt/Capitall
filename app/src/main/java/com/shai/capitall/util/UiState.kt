package com.shai.capitall.util

/**
 * מצב UI אחיד למסכים מבוססי-נתונים: טעינה / הצלחה / ריק / שגיאה.
 * מאפשר להבחין בין "אין נתונים" לבין "טעינה נכשלה" במקום לבלוע שגיאות לרשימה ריקה.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val messageRes: Int) : UiState<Nothing>
}
