package com.shai.capitall.ui.addstock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shai.capitall.data.model.StockSearchResult
import com.shai.capitall.data.repository.StockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L

class AddStockViewModel(
    private val stockRepository: StockRepository = com.shai.capitall.di.ServiceLocator.stockRepository
) : ViewModel() {

    private val _searchResults = MutableLiveData<List<StockSearchResult>>(emptyList())
    val searchResults: LiveData<List<StockSearchResult>> = _searchResults

    private val _selectedStock = MutableLiveData<StockSearchResult?>(null)
    val selectedStock: LiveData<StockSearchResult?> = _selectedStock

    private val _currentPrice = MutableLiveData<Double?>(null)
    val currentPrice: LiveData<Double?> = _currentPrice

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _searchResults.value = try {
                stockRepository.searchSymbols(query)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun onResultSelected(result: StockSearchResult) {
        _selectedStock.value = result
        _searchResults.value = emptyList()
        _currentPrice.value = null
        viewModelScope.launch {
            _currentPrice.value = try {
                stockRepository.getCurrentPrice(result.symbol)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun clearSelection() {
        _selectedStock.value = null
        _currentPrice.value = null
    }
}
