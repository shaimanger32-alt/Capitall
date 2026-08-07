package com.shai.capitall.ui.spaces

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R
import com.shai.capitall.data.model.Space
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.databinding.ItemSpaceTransactionBinding
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CurrencyConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class SpaceTransactionAdapter(
    private var space: Space?,
    private val onLongClick: (Transaction) -> Unit
) : RecyclerView.Adapter<SpaceTransactionAdapter.TxViewHolder>() {

    private val items = mutableListOf<Transaction>()
    private val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    fun submit(space: Space?, transactions: List<Transaction>) {
        this.space = space
        items.clear()
        items.addAll(transactions)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TxViewHolder(
        ItemSpaceTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        dateFormat
    )

    override fun onBindViewHolder(holder: TxViewHolder, position: Int) =
        holder.bind(items[position], space, onLongClick)

    override fun getItemCount() = items.size

    class TxViewHolder(
        private val binding: ItemSpaceTransactionBinding,
        private val dateFormat: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tx: Transaction, space: Space?, onLongClick: (Transaction) -> Unit) {
            val context = binding.root.context
            binding.tvTxMerchant.text = tx.merchant

            val categoryColor = Color.parseColor(CategoryCatalog.colorFor(tx.category))
            binding.tvTxCategory.text = CategoryCatalog.labelFor(context, tx.category)
            binding.tvTxCategory.setTextColor(categoryColor)
            val badge = binding.tvTxCategory.background.mutate() as GradientDrawable
            badge.setColor(
                Color.argb(50, Color.red(categoryColor), Color.green(categoryColor), Color.blue(categoryColor))
            )
            binding.tvTxCategory.background = badge

            // "מי שילם" הוא הנתון שהופך רשימה משותפת לפנקס — בלעדיו אין מאזן
            val payerName = space?.nameOf(tx.payerId) ?: tx.payerId.take(6)
            binding.tvTxPayer.text = context.getString(
                R.string.space_paid_by_on, payerName, dateFormat.format(Date(tx.timestamp))
            )

            val isIncome = tx.amount > 0
            val formatted = CurrencyConverter.ilsFormatterCompact().format(abs(tx.amount))
            binding.tvTxAmount.text = BidiFormatter.getInstance().unicodeWrap(
                (if (isIncome) "+" else "-") + formatted, TextDirectionHeuristicsCompat.LTR
            )
            binding.tvTxAmount.setTextColor(
                context.getColor(if (isIncome) R.color.green_positive else R.color.red_negative)
            )

            binding.root.setOnLongClickListener { onLongClick(tx); true }
        }
    }
}
