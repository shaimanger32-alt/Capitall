package com.shai.capitall.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.databinding.ItemKpiCardBinding

data class KpiCard(val label: String, val value: String, val colorRes: Int)

class KpiAdapter(private val cards: List<KpiCard>) :
    RecyclerView.Adapter<KpiAdapter.KpiViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KpiViewHolder {
        val binding = ItemKpiCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return KpiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KpiViewHolder, position: Int) {
        val card = cards[position]
        val color = holder.binding.root.context.getColor(card.colorRes)
        holder.binding.tvKpiLabel.text = card.label
        holder.binding.tvKpiValue.text = card.value
        holder.binding.tvKpiValue.setTextColor(color)
        // פס האקסנט מקבל את צבע ה-KPI — קידוד צבע עקבי בין הפס למספר
        holder.binding.kpiAccent.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    override fun getItemCount() = cards.size

    class KpiViewHolder(val binding: ItemKpiCardBinding) :
        RecyclerView.ViewHolder(binding.root)
}