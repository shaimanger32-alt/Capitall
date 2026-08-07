package com.shai.capitall.ui.spaces

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.R
import com.shai.capitall.data.model.Space
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.data.repository.SpaceRepository
import com.shai.capitall.data.repository.TransactionRepository
import com.shai.capitall.util.SpaceBalance
import kotlinx.coroutines.launch

data class SpaceDetailUiState(
    val space: Space? = null,
    val transactions: List<Transaction> = emptyList(),
    val balances: List<SpaceBalance.MemberBalance> = emptyList(),
    val settlements: List<SpaceBalance.Settlement> = emptyList(),
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val isLoading: Boolean = true,
    val isError: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && !isError && transactions.isEmpty()
}

/**
 * מסך תיק משותף: הפנקס של כל החברים, ומאזן "מי חייב למי".
 *
 * שני מקורות נפרדים בזמן אמת — מסמך התיק (חברים ושמות) והעסקאות שלו. כל שינוי
 * באחד מהם מחשב מחדש את המאזן, כי הוספת חבר משנה את החלוקה גם בלי עסקה חדשה.
 */
class SpaceDetailViewModel(
    private val spaceId: String,
    private val transactionRepository: TransactionRepository =
        com.shai.capitall.di.ServiceLocator.transactionRepository,
    private val spaceRepository: SpaceRepository = com.shai.capitall.di.ServiceLocator.spaceRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableLiveData(SpaceDetailUiState())
    val uiState: LiveData<SpaceDetailUiState> = _uiState

    private val _closed = MutableLiveData(false)
    val closed: LiveData<Boolean> = _closed

    private var space: Space? = null
    private var transactions: List<Transaction> = emptyList()

    val currentUserId: String get() = auth.currentUser?.uid.orEmpty()

    init {
        viewModelScope.launch {
            spaceRepository.observeSpaces(currentUserId).collect { spaces ->
                val found = spaces.firstOrNull { it.id == spaceId }
                if (found == null) {
                    // הוסרנו מהתיק (או שנמחק) — סוגרים את המסך במקום להציג נתונים מתים
                    _closed.value = true
                } else {
                    space = found
                    recompute()
                }
            }
        }
        viewModelScope.launch {
            transactionRepository.observeSpaceTransactions(spaceId).collect { result ->
                result.fold(
                    onSuccess = {
                        transactions = it
                        recompute()
                    },
                    onFailure = {
                        _uiState.value = _uiState.value?.copy(isLoading = false, isError = true)
                    }
                )
            }
        }
    }

    private fun recompute() {
        val current = space ?: return
        val balances = SpaceBalance.balances(transactions, current.memberIds)
        _uiState.value = SpaceDetailUiState(
            space = current,
            transactions = transactions,
            balances = balances,
            settlements = SpaceBalance.settlements(balances),
            totalExpenses = SpaceBalance.totalExpenses(transactions),
            totalIncome = SpaceBalance.totalIncome(transactions),
            isLoading = false
        )
    }

    /** מוסיף עסקה לתיק. המשלם ברירת המחדל הוא המשתמש הנוכחי. */
    fun addTransaction(
        merchant: String,
        category: String,
        amountText: String,
        isIncome: Boolean,
        payerId: String,
        timestamp: Long,
        onResult: (Int?) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onResult(R.string.add_entry_error_no_user); return
        }
        if (merchant.isBlank()) {
            onResult(R.string.add_transaction_error_no_merchant); return
        }
        val value = amountText.toDoubleOrNull()
        if (value == null || value <= 0.0) {
            onResult(R.string.add_entry_error_invalid_value); return
        }

        val transaction = Transaction(
            userId = userId,
            merchant = merchant.trim(),
            category = category,
            amount = if (isIncome) value else -value,
            timestamp = timestamp,
            spaceId = spaceId,
            paidBy = payerId
        )
        viewModelScope.launch {
            try {
                transactionRepository.addTransaction(transaction)
                onResult(null)
            } catch (e: Exception) {
                onResult(R.string.add_transaction_error_save_failed)
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            runCatching { transactionRepository.deleteTransaction(transactionId) }
        }
    }

    fun leaveSpace(onDone: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch { onDone(spaceRepository.leaveSpace(spaceId, userId)) }
    }

    /** מחיקת התיק כולו — לבעלים בלבד. העסקאות נמחקות לפני מסמך התיק. */
    fun deleteSpace(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { transactionRepository.deleteSpaceTransactions(spaceId) }
            onDone(spaceRepository.deleteSpace(spaceId))
        }
    }

    fun isOwner(): Boolean = space?.ownerId == currentUserId
}

class SpaceDetailViewModelFactory(private val spaceId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SpaceDetailViewModel(spaceId) as T
}
