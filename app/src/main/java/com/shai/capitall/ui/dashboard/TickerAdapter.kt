package com.shai.capitall.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.databinding.ItemTickerMetricBinding

data class TickerMetric(
    val label: String,
    val value: String,
    val indicator: String,   // "▲", "▼", or "—"
    val colorRes: Int
)

class TickerAdapter(private val metrics: List<TickerMetric>) :
    RecyclerView.Adapter<TickerAdapter.TickerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TickerViewHolder {
        val binding = ItemTickerMetricBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TickerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TickerViewHolder, position: Int) {
        holder.bind(metrics[position])
    }

    override fun getItemCount() = metrics.size

    class TickerViewHolder(private val binding: ItemTickerMetricBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(metric: TickerMetric) {
            binding.tvTickerLabel.text = metric.label
            binding.tvTickerValue.text = metric.value
            binding.tvTickerIndicator.text = metric.indicator

            val color = binding.root.context.getColor(metric.colorRes)
            binding.tvTickerValue.setTextColor(color)
            binding.tvTickerIndicator.setTextColor(color)
        }
    }
}