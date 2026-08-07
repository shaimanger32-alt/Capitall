package com.shai.capitall.ui.stockchart

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shai.capitall.data.repository.ChartRange
import com.shai.capitall.data.repository.PricePoint
import com.shai.capitall.data.repository.StockRepository
import kotlinx.coroutines.launch

sealed class StockChartState {
    object Loading : StockChartState()
    data class Data(
        val range: ChartRange,
        val points: List<PricePoint>,
        val lastPrice: Double,
        val changeAmount: Double,
        val changePercent: Double
    ) : StockChartState()
    data class Error(val range: ChartRange) : StockChartState()
}

class StockChartViewModel(
    private val symbol: String,
    private val stockRepository: StockRepository = com.shai.capitall.di.ServiceLocator.stockRepository
) : ViewModel() {

    private val _state = MutableLiveData<StockChartState>(StockChartState.Loading)
    val state: LiveData<StockChartState> = _state

    var currentRange: ChartRange = ChartRange.MONTH
        private set

    init {
        loadRange(currentRange)
    }

    fun loadRange(range: ChartRange) {
        currentRange = range
        _state.value = StockChartState.Loading
        viewModelScope.launch {
            try {
                val points = stockRepository.getPriceHistory(symbol, range)
                if (points.size < 2) {
                    _state.value = StockChartState.Error(range)
                    return@launch
                }
                val first = points.first().price
                val last = points.last().price
                val changeAmount = last - first
                val changePercent = if (first > 0) changeAmount / first * 100 else 0.0
                _state.value = StockChartState.Data(range, points, last, changeAmount, changePercent)
            } catch (e: Exception) {
                _state.value = StockChartState.Error(range)
            }
        }
    }
}

class StockChartViewModelFactory(private val symbol: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StockChartViewModel(symbol) as T
    }
}
