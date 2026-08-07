package com.shai.capitall.ui.addtransaction

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.R
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.data.repository.CategoryRepository
import com.shai.capitall.data.repository.TransactionRepository
import kotlinx.coroutines.launch

sealed class AddTransactionState {
    object Idle : AddTransactionState()
    object Loading : AddTransactionState()
    object Success : AddTransactionState()
    data class Error(val messageRes: Int) : AddTransactionState()
}

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository = com.shai.capitall.di.ServiceLocator.transactionRepository,
    private val categoryRepository: CategoryRepository = com.shai.capitall.di.ServiceLocator.categoryRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableLiveData<AddTransactionState>(AddTransactionState.Idle)
    val state: LiveData<AddTransactionState> = _state

    private val _categories = MutableLiveData<List<String>>(emptyList())
    val categories: LiveData<List<String>> = _categories

    init {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                categoryRepository.observeCategories(userId).collect { list ->
                    _categories.value = list
                }
            }
        }
    }

    fun addCategory(name: String) {
        val userId = auth.currentUser?.uid ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.addCategory(userId, name.trim())
        }
    }

    fun saveTransaction(
        merchant: String,
        category: String,
        amountText: String,
        isIncome: Boolean,
        paymentMethod: String,
        notes: String,
        isRecurring: Boolean,
        timestamp: Long
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _state.value = AddTransactionState.Error(R.string.add_entry_error_no_user)
            return
        }
        if (merchant.isBlank()) {
            _state.value = AddTransactionState.Error(R.string.add_transaction_error_no_merchant)
            return
        }
        if (category.isBlank()) {
            _state.value = AddTransactionState.Error(R.string.add_transaction_error_no_category)
            return
        }
        val amountValue = amountText.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0) {
            _state.value = AddTransactionState.Error(R.string.add_entry_error_invalid_value)
            return
        }

        val finalAmount = if (isIncome) amountValue else -amountValue

        val transaction = Transaction(
            userId = userId,
            merchant = merchant.trim(),
            category = category,
            amount = finalAmount,
            timestamp = timestamp,
            paymentMethod = paymentMethod,
            notes = notes.trim(),
            isRecurring = isRecurring
        )

        _state.value = AddTransactionState.Loading
        viewModelScope.launch {
            try {
                transactionRepository.addTransaction(transaction)
                _state.value = AddTransactionState.Success
            } catch (e: Exception) {
                _state.value = AddTransactionState.Error(R.string.add_transaction_error_save_failed)
            }
        }
    }
}