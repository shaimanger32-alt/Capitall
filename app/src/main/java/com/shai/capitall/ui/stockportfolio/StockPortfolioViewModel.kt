package com.shai.capitall.ui.stockportfolio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.R
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.repository.PortfolioRepository
import com.shai.capitall.data.repository.StockRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StockHolding(
    val symbol: String,
    val name: String,
    val quantity: Double,
    val avgCost: Double,
    val currentPrice: Double,
    val marketValue: Double,
    val gainLoss: Double,
    val gainLossPercent: Double,
    val assetIds: List<String> = emptyList() // כל רשומות ה-Asset שמרכיבות את האחזקה (למחיקה)
)

sealed class StockPortfolioState {
    object Loading : StockPortfolioState()
    object Empty : StockPortfolioState()
    data class Data(
        val holdings: List<StockHolding>,
        val totalCost: Double,        // בדולר
        val totalValue: Double,       // בדולר
        val totalGainLoss: Double,    // בדולר
        val totalGainLossPercent: Double,
        val totalValueIls: Double,    // שווי התיק הכולל מומר לשקל לפי השער הנוכחי
        val usdToIls: Double          // שער ההמרה שבו נעשה השימוש
    ) : StockPortfolioState()
    data class Error(val messageRes: Int) : StockPortfolioState()
}

class StockPortfolioViewModel(
    private val marketType: AssetType = AssetType.STOCK,   // STOCK או CRYPTO — אותו מסך, מקור מחיר שונה
    private val portfolioRepository: PortfolioRepository = com.shai.capitall.di.ServiceLocator.portfolioRepository,
    private val stockRepository: StockRepository = com.shai.capitall.di.ServiceLocator.stockRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableLiveData<StockPortfolioState>(StockPortfolioState.Loading)
    val state: LiveData<StockPortfolioState> = _state

    fun refresh() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _state.value = StockPortfolioState.Error(R.string.add_entry_error_no_user)
            return
        }
        _state.value = StockPortfolioState.Loading
        viewModelScope.launch {
            try {
                // גרסת ה-Result מפיצה כשל טעינה אמיתי (במקום להציג "ריק" בטעות על שגיאת רשת/הרשאה)
                val assets = portfolioRepository.observeAssetsResult(userId).first().getOrElse {
                    _state.value = StockPortfolioState.Error(R.string.stock_portfolio_error_load_failed)
                    return@launch
                }
                val stockLots = assets.filter { it.type == marketType && !it.symbol.isNullOrBlank() }
                if (stockLots.isEmpty()) {
                    _state.value = StockPortfolioState.Empty
                    return@launch
                }

                val grouped = stockLots.groupBy { it.symbol!! }
                // מניות מתומחרות ב-Finnhub, קריפטו ב-Yahoo
                val prices = if (marketType == AssetType.CRYPTO)
                    stockRepository.getCryptoPrices(grouped.keys.toList())
                else
                    stockRepository.getCurrentPrices(grouped.keys.toList())
                val fxRate = stockRepository.getUsdToIlsRate()

                val holdings = grouped.map { (symbol, lots) ->
                    val quantity = lots.sumOf { it.quantity ?: 0.0 }
                    val costBasis = lots.sumOf { it.value }
                    val avgCost = if (quantity > 0) costBasis / quantity else 0.0
                    val currentPrice = prices[symbol] ?: avgCost
                    val marketValue = quantity * currentPrice
                    val gainLoss = marketValue - costBasis
                    val gainLossPercent = if (costBasis > 0) gainLoss / costBasis * 100 else 0.0
                    StockHolding(
                        symbol = symbol,
                        name = lots.first().name,
                        quantity = quantity,
                        avgCost = avgCost,
                        currentPrice = currentPrice,
                        marketValue = marketValue,
                        gainLoss = gainLoss,
                        gainLossPercent = gainLossPercent,
                        assetIds = lots.map { it.id }
                    )
                }.sortedByDescending { it.marketValue }

                val totalCost = holdings.sumOf { it.avgCost * it.quantity }
                val totalValue = holdings.sumOf { it.marketValue }
                val totalGainLoss = totalValue - totalCost
                val totalGainLossPercent = if (totalCost > 0) totalGainLoss / totalCost * 100 else 0.0

                _state.value = StockPortfolioState.Data(
                    holdings, totalCost, totalValue, totalGainLoss, totalGainLossPercent,
                    totalValueIls = totalValue * fxRate,
                    usdToIls = fxRate
                )
            } catch (e: Exception) {
                _state.value = StockPortfolioState.Error(R.string.stock_portfolio_error_load_failed)
            }
        }
    }

    // מוחק את כל רשומות ה-Asset שמרכיבות את האחזקה (כל העסקאות של אותו סימול), ואז מרענן
    fun deleteHolding(holding: StockHolding) {
        viewModelScope.launch {
            try {
                holding.assetIds.forEach { id ->
                    portfolioRepository.deleteAsset(id)
                }
            } catch (e: Exception) {
                _state.value = StockPortfolioState.Error(R.string.stock_portfolio_error_load_failed)
            }
            refresh()
        }
    }
}

class StockPortfolioViewModelFactory(private val marketType: AssetType) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StockPortfolioViewModel(marketType = marketType) as T
    }
}
