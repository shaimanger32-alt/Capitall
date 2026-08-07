package com.shai.capitall.ui.spaces

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.R
import com.shai.capitall.data.model.Space
import com.shai.capitall.databinding.ItemSpaceBalanceBinding
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.util.SpaceBalance
import kotlin.math.abs

/**
 * שורות המאזן. [net] חיובי מוצג בירוק ("מגיע לו") ושלילי באדום ("חייב") —
 * אותה שפת צבע כמו הכנסה והוצאה בשאר האפליקציה.
 */
class SpaceBalanceAdapter(
    private var space: Space?
) : RecyclerView.Adapter<SpaceBalanceAdapter.BalanceViewHolder>() {

    private val items = mutableListOf<SpaceBalance.MemberBalance>()

    fun submit(space: Space?, balances: List<SpaceBalance.MemberBalance>) {
        this.space = space
        items.clear()
        items.addAll(balances)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = BalanceViewHolder(
        ItemSpaceBalanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: BalanceViewHolder, position: Int) =
        holder.bind(items[position], space)

    override fun getItemCount() = items.size

    class BalanceViewHolder(private val binding: ItemSpaceBalanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(balance: SpaceBalance.MemberBalance, space: Space?) {
            val context = binding.root.context
            val format = CurrencyConverter.ilsFormatterCompact()
            val bidi = BidiFormatter.getInstance()

            binding.tvMemberName.text = space?.nameOf(balance.userId) ?: balance.userId.take(6)
            binding.tvMemberOutlay.text = context.getString(
                R.string.space_member_paid,
                bidi.unicodeWrap(format.format(balance.outlay), TextDirectionHeuristicsCompat.LTR)
            )

            val net = balance.net
            val settled = abs(net) < 1.0
            binding.tvMemberNet.text = when {
                settled -> context.getString(R.string.space_settled)
                else -> bidi.unicodeWrap(
                    (if (net > 0) "+" else "-") + format.format(abs(net)),
                    TextDirectionHeuristicsCompat.LTR
                )
            }
            binding.tvMemberNet.setTextColor(
                context.getColor(
                    when {
                        settled -> R.color.on_surface_muted
                        net > 0 -> R.color.green_positive
                        else -> R.color.red_negative
                    }
                )
            )
        }
    }
}
