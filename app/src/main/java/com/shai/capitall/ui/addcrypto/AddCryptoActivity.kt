package com.shai.capitall.ui.addcrypto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shai.capitall.R
import com.shai.capitall.data.repository.StockRepository
import com.shai.capitall.databinding.ActivityAddCryptoBinding
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.util.hapticConfirm
import kotlinx.coroutines.launch
import java.util.Locale

class AddCryptoActivity : AppCompatActivity() {

    companion object {
        const val RESULT_SYMBOL = "result_symbol"
        const val RESULT_NAME = "result_name"
        const val RESULT_QUANTITY = "result_quantity"
        const val RESULT_PRICE = "result_price"
    }

    // רשימת מטבעות פופולריים בפורמט הסמלים של Yahoo (מובטח תקין למחיר/היסטוריה)
    private data class Coin(val symbol: String, val name: String)
    private val coins = listOf(
        Coin("BTC-USD", "Bitcoin"),
        Coin("ETH-USD", "Ethereum"),
        Coin("BNB-USD", "BNB"),
        Coin("SOL-USD", "Solana"),
        Coin("XRP-USD", "XRP"),
        Coin("ADA-USD", "Cardano"),
        Coin("AVAX-USD", "Avalanche"),
        Coin("DOGE-USD", "Dogecoin"),
        Coin("DOT-USD", "Polkadot"),
        Coin("LINK-USD", "Chainlink"),
        Coin("MATIC-USD", "Polygon"),
        Coin("LTC-USD", "Litecoin"),
        Coin("TRX-USD", "TRON"),
        Coin("SHIB-USD", "Shiba Inu"),
        Coin("ATOM-USD", "Cosmos"),
        Coin("XLM-USD", "Stellar"),
        Coin("BCH-USD", "Bitcoin Cash"),
        Coin("ETC-USD", "Ethereum Classic"),
        Coin("FIL-USD", "Filecoin"),
        Coin("APT-USD", "Aptos"),
        Coin("NEAR-USD", "NEAR Protocol"),
        Coin("ALGO-USD", "Algorand"),
        Coin("VET-USD", "VeChain"),
        Coin("ICP-USD", "Internet Computer"),
        Coin("HBAR-USD", "Hedera"),
        Coin("EGLD-USD", "MultiversX"),
        Coin("XMR-USD", "Monero"),
        Coin("AAVE-USD", "Aave"),
        Coin("MKR-USD", "Maker"),
        Coin("GRT-USD", "The Graph"),
        Coin("SAND-USD", "The Sandbox"),
        Coin("MANA-USD", "Decentraland"),
        Coin("AXS-USD", "Axie Infinity"),
        Coin("XTZ-USD", "Tezos"),
        Coin("EOS-USD", "EOS"),
        Coin("FLOW-USD", "Flow")
    )

    private lateinit var binding: ActivityAddCryptoBinding
    private val stockRepository = com.shai.capitall.di.ServiceLocator.stockRepository
    private var currentPrice: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCryptoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.spinnerCoin.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            coins.map { "${it.name} (${it.symbol})" }
        )
        binding.spinnerCoin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                loadPrice(coins[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.btnContinue.setOnClickListener {
            it.hapticConfirm()
            returnSelection()
        }
    }

    private fun loadPrice(coin: Coin) {
        currentPrice = null
        binding.tvCurrentPrice.text = ""
        lifecycleScope.launch {
            val price = runCatching { stockRepository.getCryptoPrice(coin.symbol) }.getOrNull()
            if (price != null && price > 0) {
                currentPrice = price
                binding.tvCurrentPrice.text =
                    getString(R.string.add_stock_current_price_label, CurrencyConverter.formatUsd(price))
                if (binding.etPurchasePrice.text.isNullOrBlank()) {
                    binding.etPurchasePrice.setText(String.format(Locale.US, "%.2f", price))
                }
            }
        }
    }

    private fun returnSelection() {
        val coin = coins[binding.spinnerCoin.selectedItemPosition]
        val quantity = binding.etQuantity.text.toString().toDoubleOrNull()
        if (quantity == null || quantity <= 0) {
            Toast.makeText(this, getString(R.string.add_entry_error_invalid_value), Toast.LENGTH_SHORT).show()
            return
        }
        val price = binding.etPurchasePrice.text.toString().toDoubleOrNull() ?: currentPrice
        if (price == null || price <= 0) {
            Toast.makeText(this, getString(R.string.add_entry_error_invalid_value), Toast.LENGTH_SHORT).show()
            return
        }

        val data = Intent().apply {
            putExtra(RESULT_SYMBOL, coin.symbol)
            putExtra(RESULT_NAME, coin.name)
            putExtra(RESULT_QUANTITY, quantity)
            putExtra(RESULT_PRICE, price)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
