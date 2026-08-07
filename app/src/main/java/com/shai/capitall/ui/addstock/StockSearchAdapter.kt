package com.shai.capitall.ui.addstock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.data.model.StockSearchResult
import com.shai.capitall.databinding.ItemStockResultBinding

class StockSearchAdapter(
    private val onClick: (StockSearchResult) -> Unit
) : RecyclerView.Adapter<StockSearchAdapter.ResultViewHolder>() {

    private val items = mutableListOf<StockSearchResult>()

    fun submitList(newItems: List<StockSearchResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemStockResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount() = items.size

    class ResultViewHolder(private val binding: ItemStockResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(result: StockSearchResult, onClick: (StockSearchResult) -> Unit) {
            binding.tvSymbol.text = result.symbol
            binding.tvName.text = result.name
            binding.rowRoot.setOnClickListener { onClick(result) }
        }
    }
}
