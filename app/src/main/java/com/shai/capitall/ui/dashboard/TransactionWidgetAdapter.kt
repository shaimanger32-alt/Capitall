package com.shai.capitall.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.databinding.ItemTransactionWidgetBinding
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CurrencyConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * טבלת העסקאות האחרונות בדשבורד: תאריך · תגית קטגוריה · תיאור · סכום.
 * הרוחבים מיושרים לשורת העמודות שבכרטיס (activity_dashboard).
 */
class TransactionWidgetAdapter(private val transactions: List<Transaction>) :
    RecyclerView.Adapter<TransactionWidgetAdapter.TxViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxViewHolder {
        val binding = ItemTransactionWidgetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TxViewHolder(binding, dateFormat)
    }

    override fun onBindViewHolder(holder: TxViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount() = transactions.size

    class TxViewHolder(
        private val binding: ItemTransactionWidgetBinding,
        private val dateFormat: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tx: Transaction) {
            val context = binding.root.context
            val categoryColor = Color.parseColor(CategoryCatalog.colorFor(tx.category))

            binding.tvTxDate.text = dateFormat.format(Date(tx.timestamp))
            binding.tvTxMerchant.text = tx.merchant

            // תגית הקטגוריה: אותו גוון ברקע שקוף למחצה ובטקסט מלא, כמו בשאר המסכים
            binding.tvTxCategory.text = CategoryCatalog.labelFor(context, tx.category)
            binding.tvTxCategory.setTextColor(categoryColor)
            val badge = binding.tvTxCategory.background.mutate() as GradientDrawable
            badge.setColor(
                Color.argb(50, Color.red(categoryColor), Color.green(categoryColor), Color.blue(categoryColor))
            )
            binding.tvTxCategory.background = badge

            // אייקון בגודל קבוע ולא לפי המידות הטבעיות שלו — אחרת הוא בולע את תווית הקטגוריה
            val iconSize = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
            val icon = ContextCompat.getDrawable(context, CategoryCatalog.iconFor(tx.category))
                ?.mutate()
                ?.apply {
                    setBounds(0, 0, iconSize, iconSize)
                    setTintList(ColorStateList.valueOf(categoryColor))
                }
            binding.tvTxCategory.setCompoundDrawablesRelative(icon, null, null, null)

            val isIncome = tx.amount > 0
            // הסכום מוצג ללא אגורות ועטוף כקטע LTR — אחרת המינוס ותו ה-₪ מתהפכים
            // בפסקה בעברית ומתקבל "78₪-" במקום "-₪78".
            val formatted = CurrencyConverter.ilsFormatterCompact().format(abs(tx.amount))
            val signed = (if (isIncome) "+" else "-") + formatted
            binding.tvTxAmount.text = BidiFormatter.getInstance()
                .unicodeWrap(signed, TextDirectionHeuristicsCompat.LTR)
            binding.tvTxAmount.setTextColor(
                context.getColor(if (isIncome) R.color.green_positive else R.color.red_negative)
            )
        }

        private companion object {
            const val ICON_SIZE_DP = 12
        }
    }
}
