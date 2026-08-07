package com.shai.capitall.ui.stockportfolio
import com.shai.capitall.util.CurrencyConverter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R
import com.shai.capitall.databinding.ItemStockHoldingBinding
import java.text.NumberFormat
import java.util.Locale

class StockHoldingAdapter(
    private val onClick: (StockHolding) -> Unit,
    private val onDelete: (StockHolding) -> Unit
) : RecyclerView.Adapter<StockHoldingAdapter.HoldingViewHolder>() {

    private val items = mutableListOf<StockHolding>()

    fun submitList(newItems: List<StockHolding>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HoldingViewHolder {
        val binding = ItemStockHoldingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HoldingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HoldingViewHolder, position: Int) {
        holder.bind(items[position], onClick, onDelete)
    }

    override fun getItemCount() = items.size

    class HoldingViewHolder(private val binding: ItemStockHoldingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            holding: StockHolding,
            onClick: (StockHolding) -> Unit,
            onDelete: (StockHolding) -> Unit
        ) {
            val context = binding.root.context
            val currency = CurrencyConverter.usdFormatter()

            binding.tvSymbol.text = holding.symbol
            binding.tvName.text = holding.name
            binding.tvMarketValue.text = currency.format(holding.marketValue)
            // מונוגרמה: הסימול ללא סיומת שוק (למשל "BTC-USD" → "BTC")
            binding.tvTicker.text = holding.symbol.substringBefore('-').take(4)

            val sign = if (holding.gainLoss >= 0) "+" else ""
            val amountText = sign + currency.format(holding.gainLoss)
            val percentText = "$sign${String.format(Locale.US, "%.2f", holding.gainLossPercent)}%"
            binding.tvGainLoss.text = context.getString(
                R.string.stock_portfolio_gain_loss_format, amountText, percentText
            )
            val trendColor = context.getColor(
                if (holding.gainLoss >= 0) R.color.green_positive else R.color.red_negative
            )
            binding.tvGainLoss.setTextColor(trendColor)
            // באדג' רך באותו גוון — רווח/הפסד נקרא מיד בסריקת הרשימה
            binding.tvGainLoss.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(trendColor, 26)
            )

            val quantityText = if (holding.quantity == holding.quantity.toLong().toDouble()) {
                holding.quantity.toLong().toString()
            } else {
                String.format(Locale.US, "%.4f", holding.quantity)
            }
            binding.tvQuantity.text = context.getString(R.string.stock_portfolio_quantity_format, quantityText)
            binding.tvAvgCost.text = context.getString(R.string.stock_portfolio_avg_cost_format, currency.format(holding.avgCost))
            binding.tvCurrentPrice.text = context.getString(R.string.stock_portfolio_current_price_format, currency.format(holding.currentPrice))

            binding.root.setOnClickListener { onClick(holding) }
            binding.btnDelete.setOnClickListener { onDelete(holding) }
        }
    }
}
