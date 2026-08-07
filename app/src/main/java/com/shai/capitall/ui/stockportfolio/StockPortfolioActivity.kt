package com.shai.capitall.ui.stockportfolio
import com.shai.capitall.util.CurrencyConverter

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.shai.capitall.R
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.databinding.ActivityStockPortfolioBinding
import com.shai.capitall.ui.stockchart.StockChartBottomSheet
import com.shai.capitall.util.playRiseAnimation
import com.shai.capitall.util.bindEmptyState
import com.shai.capitall.util.startPulse
import java.text.NumberFormat
import java.util.Locale

class StockPortfolioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MARKET_TYPE = "extra_market_type"
    }

    private lateinit var binding: ActivityStockPortfolioBinding
    private lateinit var viewModel: StockPortfolioViewModel
    private lateinit var adapter: StockHoldingAdapter
    private var isCrypto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockPortfolioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isCrypto = intent.getStringExtra(EXTRA_MARKET_TYPE) == AssetType.CRYPTO.name
        val marketType = if (isCrypto) AssetType.CRYPTO else AssetType.STOCK

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.title = getString(
            if (isCrypto) R.string.crypto_portfolio_title else R.string.stock_portfolio_title
        )
        // מצב ריק מונחה-פעולה; גוף ההסבר מותאם למניות/קריפטו
        binding.tvEmptyState.root.bindEmptyState(
            titleRes = R.string.empty_stocks_title,
            bodyRes = if (isCrypto) R.string.crypto_portfolio_empty_state else R.string.empty_stocks_body
        )

        viewModel = ViewModelProvider(this, StockPortfolioViewModelFactory(marketType))[StockPortfolioViewModel::class.java]

        adapter = StockHoldingAdapter(
            onClick = { holding ->
                StockChartBottomSheet.newInstance(holding.symbol, holding.name)
                    .show(supportFragmentManager, "stock_chart")
            },
            onDelete = { holding -> confirmDeleteHolding(holding) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.state.observe(this) { state -> render(state) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun confirmDeleteHolding(holding: StockHolding) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.portfolio_delete_title, holding.symbol))
            .setMessage(R.string.portfolio_delete_message)
            .setPositiveButton(R.string.portfolio_delete_confirm) { _, _ -> viewModel.deleteHolding(holding) }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    private fun render(state: StockPortfolioState) {
        val loading = state is StockPortfolioState.Loading
        binding.progressBar.root.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.progressBar.root.startPulse()
        binding.summaryCard.visibility = if (state is StockPortfolioState.Data) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (state is StockPortfolioState.Data) View.VISIBLE else View.GONE
        binding.tvEmptyState.root.visibility = if (state is StockPortfolioState.Empty) View.VISIBLE else View.GONE

        when (state) {
            is StockPortfolioState.Data -> {
                val currency = CurrencyConverter.usdFormatter()
                val ils = CurrencyConverter.ilsFormatter()
                adapter.submitList(state.holdings)
                binding.recyclerView.playRiseAnimation()
                binding.tvTotalInvested.text = currency.format(state.totalCost)
                binding.tvCurrentValue.text = currency.format(state.totalValue)
                // שווי התיק הכולל מומר לשקל — כפי שנכנס לשווי הנקי
                binding.tvCurrentValueIls.text =
                    getString(R.string.stock_portfolio_ils_equivalent, ils.format(state.totalValueIls))
                binding.tvFxRate.text =
                    getString(R.string.stock_portfolio_fx_rate, ils.format(state.usdToIls))

                val sign = if (state.totalGainLoss >= 0) "+" else ""
                val amountText = sign + currency.format(state.totalGainLoss)
                val percentText = "$sign${String.format(Locale.US, "%.2f", state.totalGainLossPercent)}%"
                binding.tvTotalGainLoss.text = getString(
                    R.string.stock_portfolio_gain_loss_format, amountText, percentText
                )
                val colorRes = if (state.totalGainLoss >= 0) R.color.green_positive else R.color.red_negative
                binding.tvTotalGainLoss.setTextColor(getColor(colorRes))
            }
            is StockPortfolioState.Error -> {
                Toast.makeText(this, getString(state.messageRes), Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }
}
