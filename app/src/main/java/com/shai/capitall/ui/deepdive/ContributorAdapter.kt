package com.shai.capitall.ui.deepdive
import com.shai.capitall.util.CurrencyConverter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.databinding.ItemContributorRowBinding
import java.text.NumberFormat
import java.util.Locale

class ContributorAdapter(private val contributors: List<Contributor>) :
    RecyclerView.Adapter<ContributorAdapter.ContributorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContributorViewHolder {
        val binding = ItemContributorRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContributorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContributorViewHolder, position: Int) {
        val contributor = contributors[position]
        val format = CurrencyConverter.ilsFormatter()

        holder.binding.tvRank.text = "${position + 1}."
        holder.binding.tvContributorName.text = contributor.merchant
        holder.binding.tvContributorAmount.text = format.format(contributor.amount)
        holder.binding.progressContributor.progress = contributor.percent
    }

    override fun getItemCount() = contributors.size

    class ContributorViewHolder(val binding: ItemContributorRowBinding) :
        RecyclerView.ViewHolder(binding.root)
}