package com.shai.capitall.ui.screener
import com.shai.capitall.util.CurrencyConverter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.databinding.ItemTransactionRowBinding
import com.shai.capitall.util.CategoryCatalog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionTableAdapter(
    private val onRowClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionTableAdapter.RowViewHolder>() {

    private val items = mutableListOf<Transaction>()
    private val dateFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)

    fun submitList(newItems: List<Transaction>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemTransactionRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(items[position], dateFormat, onRowClick)
    }

    override fun getItemCount() = items.size

    class RowViewHolder(private val binding: ItemTransactionRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tx: Transaction, dateFormat: SimpleDateFormat, onRowClick: (Transaction) -> Unit) {
            val format = CurrencyConverter.ilsFormatter()

            binding.tvRowDate.text = dateFormat.format(Date(tx.timestamp))
            binding.tvRowMerchant.text = tx.merchant

            binding.tvRowCategoryBadge.text = CategoryCatalog.labelFor(binding.root.context, tx.category)
            val categoryColor = Color.parseColor(CategoryCatalog.colorFor(tx.category))
            binding.tvRowCategoryBadge.setTextColor(categoryColor)
            val badgeDrawable = binding.tvRowCategoryBadge.background.mutate()
            (badgeDrawable as android.graphics.drawable.GradientDrawable).setColor(
                Color.argb(50, Color.red(categoryColor), Color.green(categoryColor), Color.blue(categoryColor))
            )
            binding.tvRowCategoryBadge.background = badgeDrawable

            val isIncome = tx.amount > 0
            val sign = if (isIncome) "+" else ""
            binding.tvRowAmount.text = "$sign${format.format(tx.amount)}"
            binding.tvRowAmount.setTextColor(
                binding.root.context.getColor(
                    if (isIncome) R.color.green_positive else R.color.red_negative
                )
            )

            binding.root.setOnClickListener { onRowClick(tx) }
        }
    }
}