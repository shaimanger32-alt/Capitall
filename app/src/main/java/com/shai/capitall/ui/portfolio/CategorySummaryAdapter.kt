package com.shai.capitall.ui.portfolio
import com.shai.capitall.util.CurrencyConverter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R
import com.shai.capitall.databinding.ItemCategorySummaryBinding
import com.shai.capitall.util.CategoryCatalog
import java.text.NumberFormat
import java.util.Locale

class CategorySummaryAdapter(
    private val onClick: (CategorySummaryRow) -> Unit
) : RecyclerView.Adapter<CategorySummaryAdapter.RowViewHolder>() {

    private val items = mutableListOf<CategorySummaryRow>()

    fun submitList(newItems: List<CategorySummaryRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemCategorySummaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount() = items.size

    class RowViewHolder(private val binding: ItemCategorySummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: CategorySummaryRow, onClick: (CategorySummaryRow) -> Unit) {
            val context = binding.root.context
            val format = CurrencyConverter.ilsFormatter()

            binding.tvCategoryLabel.text = CategoryCatalog.labelFor(context, row.categoryKey)
            binding.tvCategoryCount.text = context.resources.getQuantityString(
                R.plurals.portfolio_category_item_count, row.count, row.count
            )
            binding.tvCategoryTotal.text = format.format(row.totalValue)

            // סוואטש: נקודה מלאה בצבע הקטגוריה בתוך ריבוע רך באותו גוון
            val categoryColor = Color.parseColor(row.colorHex)
            binding.viewColorDot.setImageResource(CategoryCatalog.iconFor(row.categoryKey))
            binding.viewColorDot.setColorFilter(categoryColor)
            binding.categoryIconBg.background.mutate().setTint(
                androidx.core.graphics.ColorUtils.setAlphaComponent(categoryColor, 38)
            )

            binding.root.setOnClickListener { onClick(row) }
        }
    }
}
