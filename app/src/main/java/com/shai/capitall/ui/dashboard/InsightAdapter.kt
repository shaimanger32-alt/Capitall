package com.shai.capitall.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.databinding.ItemInsightBinding

class InsightAdapter(private val insights: List<String>) :
    RecyclerView.Adapter<InsightAdapter.InsightViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InsightViewHolder {
        val binding = ItemInsightBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return InsightViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InsightViewHolder, position: Int) {
        holder.binding.tvInsightText.text = insights[position]
    }

    override fun getItemCount() = insights.size

    class InsightViewHolder(val binding: ItemInsightBinding) :
        RecyclerView.ViewHolder(binding.root)
}