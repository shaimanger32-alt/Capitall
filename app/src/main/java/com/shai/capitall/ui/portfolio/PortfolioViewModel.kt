package com.shai.capitall.ui.portfolio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.R
import com.shai.capitall.data.model.Asset
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.model.Liability
import com.shai.capitall.data.repository.PortfolioRepository
import com.shai.capitall.data.repository.StockRepository
import com.shai.capitall.util.AssetValuation
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.util.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class CategorySummaryRow(
    val categoryKey: String,
    val colorHex: String,
    val totalValue: Double,
    val count: Int,
    val isAsset: Boolean
)

data class PortfolioData(
    val assetGroups: List<CategorySummaryRow>,
    val liabilityGroups: List<CategorySummaryRow>
)

class PortfolioViewModel(
    private val portfolioRepository: PortfolioRepository = com.shai.capitall.di.ServiceLocator.portfolioRepository,
    private val stockRepository: StockRepository = com.shai.capitall.di.ServiceLocator.stockRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableLiveData<UiState<PortfolioData>>(UiState.Loading)
    val uiState: LiveData<UiState<PortfolioData>> = _uiState

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.value = UiState.Error(R.string.error_load_failed)
            return
        }
        loadJob?.cancel()
        _uiState.value = UiState.Loading
        loadJob = viewModelScope.launch {
            // מרעננים את שער הדולר/שקל לפני החישוב כדי שערכי המניות יומרו לשקל לפי שער עדכני
            runCatching { stockRepository.getUsdToIlsRate() }
            combine(
                portfolioRepository.observeAssetsResult(userId),
                portfolioRepository.observeLiabilitiesResult(userId)
            ) { assetsResult, liabilitiesResult ->
                if (assetsResult.isFailure || liabilitiesResult.isFailure) {
                    UiState.Error(R.string.error_load_failed)
                } else {
                    val data = PortfolioData(
                        assetGroups = buildAssetGroups(assetsResult.getOrDefault(emptyList())),
                        liabilityGroups = buildLiabilityGroups(liabilitiesResult.getOrDefault(emptyList()))
                    )
                    if (data.assetGroups.isEmpty() && data.liabilityGroups.isEmpty()) UiState.Empty
                    else UiState.Success(data)
                }
            }.collect { state -> _uiState.value = state }
        }
    }

    private fun buildAssetGroups(assets: List<Asset>): List<CategorySummaryRow> =
        assets.groupBy { it.category }.map { (category, entries) ->
            CategorySummaryRow(
                categoryKey = category,
                colorHex = CategoryCatalog.colorFor(category),
                // נכסים נסחרים (מניות/קריפטו) מוצגים בעלות הקנייה (בדולר) מומרת לשקל; שאר הנכסים משוערכים
                totalValue = entries.sumOf {
                    if (it.type == AssetType.STOCK || it.type == AssetType.CRYPTO) CurrencyConverter.usdToIls(it.value)
                    else AssetValuation.projectedAssetValue(it)
                },
                count = entries.size,
                isAsset = true
            )
        }.sortedByDescending { it.totalValue }

    private fun buildLiabilityGroups(liabilities: List<Liability>): List<CategorySummaryRow> =
        liabilities.groupBy { it.category }.map { (category, entries) ->
            CategorySummaryRow(
                categoryKey = category,
                colorHex = CategoryCatalog.colorFor(category),
                totalValue = entries.sumOf { AssetValuation.projectedLiabilityValue(it) },
                count = entries.size,
                isAsset = false
            )
        }.sortedByDescending { it.totalValue }
}
