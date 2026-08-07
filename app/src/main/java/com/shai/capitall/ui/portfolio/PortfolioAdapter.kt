package com.shai.capitall.ui.portfolio
import com.shai.capitall.util.CurrencyConverter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R
import com.shai.capitall.databinding.ItemAssetBinding
import com.shai.capitall.util.CategoryCatalog
import java.text.NumberFormat
import java.util.Locale

data class PortfolioRow(
    val id: String,
    val name: String,
    val category: String,
    val value: Double,
    val isAsset: Boolean,
    val recurringAmount: Double? = null,
    val changePercent: Double? = null // שינוי שנתי מצטבר מאז הרכישה (null = אין שיעור לקטגוריה)
)

class PortfolioAdapter(
    private val onDelete: (PortfolioRow) -> Unit
) : RecyclerView.Adapter<PortfolioAdapter.RowViewHolder>() {

    private val items = mutableListOf<PortfolioRow>()

    fun submitList(newItems: List<PortfolioRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemAssetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(items[position], onDelete)
    }

    override fun getItemCount() = items.size

    class RowViewHolder(private val binding: ItemAssetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: PortfolioRow, onDelete: (PortfolioRow) -> Unit) {
            val context = binding.root.context
            val format = CurrencyConverter.ilsFormatter()
            binding.tvItemName.text = row.name

            val categoryLabel = CategoryCatalog.labelFor(context, row.category)
            val categoryDef = CategoryCatalog.byKey(row.category)
            binding.tvItemCategory.text = if (
                row.recurringAmount != null && row.recurringAmount > 0 && categoryDef?.recurringOptionLabelRes != null
            ) {
                context.getString(
                    R.string.portfolio_category_with_recurring,
                    categoryLabel,
                    context.getString(categoryDef.recurringOptionLabelRes),
                    format.format(row.recurringAmount)
                )
            } else {
                categoryLabel
            }

            binding.tvItemValue.text = format.format(row.value)
            binding.tvItemValue.setTextColor(context.getColor(R.color.on_surface))

            // סוואטש בצבע הקטגוריה — עוגן ויזואלי שמאפשר סריקה מהירה של הרשימה
            val categoryColor = android.graphics.Color.parseColor(CategoryCatalog.colorFor(row.category))
            binding.itemIconBg.background.mutate().setTint(
                androidx.core.graphics.ColorUtils.setAlphaComponent(categoryColor, 38)
            )
            binding.itemDot.setImageResource(CategoryCatalog.iconFor(row.category))
            binding.itemDot.setColorFilter(categoryColor)

            // באדג' שינוי שנתי מצטבר מאז הרכישה (ירוק=עלייה בשווי נטו, אדום=ירידה)
            val change = row.changePercent
            if (change != null) {
                val sign = if (change >= 0) "+" else ""
                binding.tvItemChange.text = "$sign${String.format(Locale.US, "%.1f", change)}%"
                // לנכס: עלייה=ירוק. להתחייבות: גדילת חוב=אדום (רע), לכן הצבע הפוך
                val positive = if (row.isAsset) change >= 0 else change <= 0
                val trendColor = context.getColor(
                    if (positive) R.color.green_positive else R.color.red_negative
                )
                binding.tvItemChange.setTextColor(trendColor)
                binding.tvItemChange.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(trendColor, 26)
                )
                binding.tvItemChange.visibility = android.view.View.VISIBLE
            } else {
                binding.tvItemChange.visibility = android.view.View.GONE
            }

            binding.btnDelete.setOnClickListener { onDelete(row) }
            binding.root.setOnLongClickListener {
                onDelete(row)
                true
            }
        }
    }
}