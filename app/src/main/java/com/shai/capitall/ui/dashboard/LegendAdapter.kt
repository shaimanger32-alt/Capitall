package com.shai.capitall.ui.dashboard
import com.shai.capitall.util.CurrencyConverter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.databinding.ItemLegendBinding
import java.text.NumberFormat
import java.util.Locale

data class CategoryAmount(val label: String, val amount: Float, val colorHex: String)

class LegendAdapter(private val categories: List<CategoryAmount>) :
    RecyclerView.Adapter<LegendAdapter.LegendViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegendViewHolder {
        val binding = ItemLegendBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LegendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LegendViewHolder, position: Int) {
        val category = categories[position]
        val format = CurrencyConverter.ilsFormatter()

        holder.binding.tvLegendName.text = category.label
        holder.binding.tvLegendValue.text = format.format(category.amount)

        val dotDrawable = holder.binding.viewLegendDot.background as GradientDrawable
        dotDrawable.setColor(Color.parseColor(category.colorHex))
    }

    override fun getItemCount() = categories.size

    class LegendViewHolder(val binding: ItemLegendBinding) :
        RecyclerView.ViewHolder(binding.root)
}