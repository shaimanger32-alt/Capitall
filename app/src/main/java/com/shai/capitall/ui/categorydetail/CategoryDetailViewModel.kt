package com.shai.capitall.ui.categorydetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.repository.PortfolioRepository
import com.shai.capitall.data.repository.StockRepository
import com.shai.capitall.ui.portfolio.PortfolioRow
import com.shai.capitall.util.AssetValuation
import com.shai.capitall.util.CurrencyConverter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CategoryDetailViewModel(
    private val category: String,
    private val isAsset: Boolean,
    private val portfolioRepository: PortfolioRepository = com.shai.capitall.di.ServiceLocator.portfolioRepository,
    private val stockRepository: StockRepository = com.shai.capitall.di.ServiceLocator.stockRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _rows = MutableLiveData<List<PortfolioRow>>(emptyList())
    val rows: LiveData<List<PortfolioRow>> = _rows

    // מצב שגיאת טעינה — המסך מציג עליו באנר "נסה שוב" (עקבי עם שאר המסכים)
    private val _isError = MutableLiveData(false)
    val isError: LiveData<Boolean> = _isError

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        val userId = auth.currentUser?.uid ?: return
        loadJob?.cancel()
        _isError.value = false
        loadJob = viewModelScope.launch {
            // מרעננים שער דולר/שקל לפני החישוב כדי שערכי המניות יומרו לשקל לפי שער עדכני
            runCatching { stockRepository.getUsdToIlsRate() }
            if (isAsset) {
                portfolioRepository.observeAssetsResult(userId).collect { result ->
                    result.fold(
                        onSuccess = { assets ->
                            _isError.value = false
                            _rows.value = assets
                                .filter { it.category == category }
                                .map { asset ->
                                    // נכסים נסחרים (מניות/קריפטו): עלות הקנייה בדולר מומרת לשקל; שאר הנכסים משוערכים
                                    val isMarket = asset.type == AssetType.STOCK || asset.type == AssetType.CRYPTO
                                    val current = if (isMarket) CurrencyConverter.usdToIls(asset.value)
                                    else AssetValuation.projectedAssetValue(asset)
                                    val change = if (isMarket) null else AssetValuation.assetChangePercent(asset)
                                    PortfolioRow(asset.id, asset.name, asset.category, current, isAsset = true, recurringAmount = asset.recurringIncomeAmount, changePercent = change)
                                }
                        },
                        onFailure = { _isError.value = true }
                    )
                }
            } else {
                portfolioRepository.observeLiabilitiesResult(userId).collect { result ->
                    result.fold(
                        onSuccess = { liabilities ->
                            _isError.value = false
                            _rows.value = liabilities
                                .filter { it.category == category }
                                .map { liability ->
                                    val current = AssetValuation.projectedLiabilityValue(liability)
                                    val change = AssetValuation.liabilityChangePercent(liability)
                                    PortfolioRow(liability.id, liability.name, liability.category, current, isAsset = false, recurringAmount = liability.recurringPaymentAmount, changePercent = change)
                                }
                        },
                        onFailure = { _isError.value = true }
                    )
                }
            }
        }
    }

    fun retry() = load()

    fun deleteRow(row: PortfolioRow) {
        viewModelScope.launch {
            if (row.isAsset) {
                portfolioRepository.deleteAsset(row.id)
            } else {
                portfolioRepository.deleteLiability(row.id)
            }
        }
    }
}

class CategoryDetailViewModelFactory(
    private val category: String,
    private val isAsset: Boolean
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CategoryDetailViewModel(category, isAsset) as T
    }
}
