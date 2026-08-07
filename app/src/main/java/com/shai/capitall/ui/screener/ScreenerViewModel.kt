package com.shai.capitall.ui.screener

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.data.repository.TransactionRepository
import kotlinx.coroutines.launch
import java.util.Calendar

enum class DateRangeFilter { THIS_MONTH, LAST_QUARTER, YEAR_TO_DATE, ALL }
enum class TypeFilter { ALL, EXPENSES, INCOME }
enum class SortColumn { DATE, MERCHANT, CATEGORY, AMOUNT }

data class ScreenerUiState(
    val rows: List<Transaction> = emptyList(),
    val count: Int = 0,
    val totalExpense: Double = 0.0,
    val averagePerTransaction: Double = 0.0
)

class ScreenerViewModel(
    private val transactionRepository: TransactionRepository = com.shai.capitall.di.ServiceLocator.transactionRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private var allTransactions: List<Transaction> = emptyList()

    private var dateRange = DateRangeFilter.ALL
    private var typeFilter = TypeFilter.ALL
    private var selectedCategories = mutableSetOf<String>()
    private var minAmount: Double? = null
    private var maxAmount: Double? = null
    private var sortColumn = SortColumn.DATE
    private var sortAscending = false

    private val _uiState = MutableLiveData(ScreenerUiState())
    val uiState: LiveData<ScreenerUiState> = _uiState

    init {
        applyFilters()
        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                transactionRepository.observeTransactions(userId).collect { list ->
                    allTransactions = list
                    applyFilters()
                }
            }
        }
    }

    fun setDateRange(range: DateRangeFilter) {
        dateRange = range
        applyFilters()
    }

    fun setTypeFilter(type: TypeFilter) {
        typeFilter = type
        applyFilters()
    }

    fun setSelectedCategories(categories: Set<String>) {
        selectedCategories = categories.toMutableSet()
        applyFilters()
    }

    fun setAmountRange(min: Double?, max: Double?) {
        minAmount = min
        maxAmount = max
        applyFilters()
    }

    fun clearAllFilters() {
        dateRange = DateRangeFilter.ALL
        typeFilter = TypeFilter.ALL
        selectedCategories.clear()
        minAmount = null
        maxAmount = null
        applyFilters()
    }

    fun sortBy(column: SortColumn) {
        if (sortColumn == column) {
            sortAscending = !sortAscending
        } else {
            sortColumn = column
            sortAscending = true
        }
        applyFilters()
    }

    fun getSelectedCategories(): Set<String> = selectedCategories

    fun deleteTransaction(tx: Transaction) {
        if (tx.id.isBlank()) return
        viewModelScope.launch { transactionRepository.deleteTransaction(tx.id) }
    }

    fun updateTransaction(tx: Transaction, category: String, notes: String, isRecurring: Boolean) {
        if (tx.id.isBlank()) return
        viewModelScope.launch {
            transactionRepository.updateTransaction(
                tx.id,
                mapOf(
                    "category" to category,
                    "notes" to notes,
                    "isRecurring" to isRecurring
                )
            )
        }
    }

    private fun applyFilters() {
        var filtered = allTransactions.toList()

        // סינון טווח תאריכים
        val cal = Calendar.getInstance()
        filtered = when (dateRange) {
            DateRangeFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                filtered.filter { it.timestamp >= cal.timeInMillis }
            }
            DateRangeFilter.LAST_QUARTER -> {
                cal.add(Calendar.MONTH, -3)
                filtered.filter { it.timestamp >= cal.timeInMillis }
            }
            DateRangeFilter.YEAR_TO_DATE -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                filtered.filter { it.timestamp >= cal.timeInMillis }
            }
            DateRangeFilter.ALL -> filtered
        }

        // סינון סוג
        filtered = when (typeFilter) {
            TypeFilter.EXPENSES -> filtered.filter { it.amount < 0 }
            TypeFilter.INCOME -> filtered.filter { it.amount > 0 }
            TypeFilter.ALL -> filtered
        }

        // סינון קטגוריות
        if (selectedCategories.isNotEmpty()) {
            filtered = filtered.filter { it.category in selectedCategories }
        }

        // סינון טווח סכום (לפי ערך מוחלט, כדי שיתאים גם להוצאות וגם להכנסות)
        minAmount?.let { min -> filtered = filtered.filter { kotlin.math.abs(it.amount) >= min } }
        maxAmount?.let { max -> filtered = filtered.filter { kotlin.math.abs(it.amount) <= max } }

        // מיון
        filtered = when (sortColumn) {
            SortColumn.DATE -> filtered.sortedBy { it.timestamp }
            SortColumn.MERCHANT -> filtered.sortedBy { it.merchant }
            SortColumn.CATEGORY -> filtered.sortedBy { it.category }
            SortColumn.AMOUNT -> filtered.sortedBy { it.amount }
        }
        if (!sortAscending) filtered = filtered.reversed()

        val expenses = filtered.filter { it.amount < 0 }
        val totalExpense = expenses.sumOf { kotlin.math.abs(it.amount) }
        val avg = if (filtered.isNotEmpty()) {
            filtered.sumOf { kotlin.math.abs(it.amount) } / filtered.size
        } else 0.0

        _uiState.value = ScreenerUiState(
            rows = filtered,
            count = filtered.size,
            totalExpense = totalExpense,
            averagePerTransaction = avg
        )
    }
}