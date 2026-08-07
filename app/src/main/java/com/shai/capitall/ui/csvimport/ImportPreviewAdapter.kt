package com.shai.capitall.ui.csvimport

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R
import com.shai.capitall.databinding.ItemImportRowBinding
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.util.csv.CategorySource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * שורות התצוגה המקדימה של היבוא. לחיצה על השורה מסמנת/מבטלת אותה, ולחיצה על תג
 * הקטגוריה פותחת בורר — כך שהמשתמש מתקן את הניחושים לפני שהם נכתבים ל-Firestore.
 */
class ImportPreviewAdapter(
    private val onToggle: (Int) -> Unit,
    private val onCategoryClick: (Int) -> Unit
) : RecyclerView.Adapter<ImportPreviewAdapter.RowViewHolder>() {

    private val items = mutableListOf<StagedRow>()
    private val dateFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault())

    fun submitList(newItems: List<StagedRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemImportRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RowViewHolder(binding, dateFormat)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(items[position], onToggle, onCategoryClick)
    }

    override fun getItemCount() = items.size

    class RowViewHolder(
        private val binding: ItemImportRowBinding,
        private val dateFormat: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(staged: StagedRow, onToggle: (Int) -> Unit, onCategoryClick: (Int) -> Unit) {
            val context = binding.root.context
            val format = CurrencyConverter.ilsFormatter()

            binding.cbSelected.isChecked = staged.isSelected
            binding.tvMerchant.text = staged.row.merchant.ifBlank {
                context.getString(R.string.import_unknown_merchant)
            }
            binding.tvDate.text = dateFormat.format(Date(staged.row.timestamp))

            // תג הקטגוריה בצבע הקטגוריה, עם רקע באותו גוון בשקיפות — כמו בשאר המסכים
            binding.tvCategoryBadge.text = CategoryCatalog.labelFor(context, staged.category)
            val categoryColor = Color.parseColor(CategoryCatalog.colorFor(staged.category))
            binding.tvCategoryBadge.setTextColor(categoryColor)
            val badge = binding.tvCategoryBadge.background.mutate() as GradientDrawable
            badge.setColor(
                Color.argb(50, Color.red(categoryColor), Color.green(categoryColor), Color.blue(categoryColor))
            )
            binding.tvCategoryBadge.background = badge

            val isIncome = staged.row.amount > 0
            binding.tvAmount.text = (if (isIncome) "+" else "") + format.format(staged.row.amount)
            binding.tvAmount.setTextColor(
                context.getColor(if (isIncome) R.color.green_positive else R.color.red_negative)
            )

            binding.tvReviewFlag.visibility =
                if (staged.source == CategorySource.FALLBACK) View.VISIBLE else View.GONE
            binding.tvDuplicateFlag.visibility =
                if (staged.isDuplicate) View.VISIBLE else View.GONE

            // שורה שלא נבחרה מוצגת מעומעמת כדי שיהיה ברור מה ייכנס בפועל
            binding.root.alpha = if (staged.isSelected) 1f else 0.45f

            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onToggle(bindingAdapterPosition)
                }
            }
            binding.tvCategoryBadge.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onCategoryClick(bindingAdapterPosition)
                }
            }
        }
    }
}
