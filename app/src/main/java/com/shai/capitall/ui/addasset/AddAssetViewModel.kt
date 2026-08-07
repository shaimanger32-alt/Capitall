package com.shai.capitall.ui.addasset

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
import kotlinx.coroutines.launch

enum class EntryType { ASSET, LIABILITY }

sealed class AddAssetState {
    object Idle : AddAssetState()
    object Loading : AddAssetState()
    object Success : AddAssetState()
    data class Error(val messageRes: Int) : AddAssetState()
}

class AddAssetViewModel(
    private val portfolioRepository: PortfolioRepository = com.shai.capitall.di.ServiceLocator.portfolioRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableLiveData<AddAssetState>(AddAssetState.Idle)
    val state: LiveData<AddAssetState> = _state

    fun saveEntry(
        type: EntryType,
        name: String,
        category: String,
        valueText: String,
        recurringAmount: Double? = null,
        dateMillis: Long = System.currentTimeMillis(),
        symbol: String? = null,
        quantity: Double? = null,
        marketType: AssetType? = null,   // STOCK / CRYPTO — נכס נסחר עם סימול; null = נכס רגיל
        annualRate: Double? = null,
        termYears: Int? = null,
        currency: String? = null         // מטבע הנקיבה — רלוונטי לקטגוריית מט"ח; null = שקל
    ) {
        val isMarket = marketType != null && symbol != null
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _state.value = AddAssetState.Error(R.string.add_entry_error_no_user)
            return
        }
        if (name.isBlank()) {
            _state.value = AddAssetState.Error(R.string.add_entry_error_no_name)
            return
        }
        val value = valueText.toDoubleOrNull()
        if (value == null || value < 0) {
            _state.value = AddAssetState.Error(R.string.add_entry_error_invalid_value)
            return
        }

        _state.value = AddAssetState.Loading
        viewModelScope.launch {
            try {
                when (type) {
                    EntryType.ASSET -> portfolioRepository.addAsset(
                        Asset(
                            userId = userId,
                            name = name.trim(),
                            type = marketType ?: AssetType.MANUAL,
                            category = category,
                            value = value,
                            symbol = if (isMarket) symbol else null,
                            quantity = if (isMarket) quantity else null,
                            currency = currency,
                            recurringIncomeAmount = recurringAmount,
                            annualRate = if (isMarket) null else annualRate,
                            createdAt = dateMillis,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    EntryType.LIABILITY -> portfolioRepository.addLiability(
                        Liability(
                            userId = userId,
                            name = name.trim(),
                            category = category,
                            value = value,
                            recurringPaymentAmount = recurringAmount,
                            annualRate = annualRate,
                            termYears = termYears,
                            createdAt = dateMillis,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                _state.value = AddAssetState.Success
            } catch (e: Exception) {
                _state.value = AddAssetState.Error(R.string.add_transaction_error_save_failed)
            }
        }
    }
}