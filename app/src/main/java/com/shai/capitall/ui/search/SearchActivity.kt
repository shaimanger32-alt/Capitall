package com.shai.capitall.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.R
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.repository.PortfolioRepository
import com.shai.capitall.data.repository.TransactionRepository
import com.shai.capitall.databinding.ActivitySearchBinding
import com.shai.capitall.databinding.ItemSearchResultBinding
import com.shai.capitall.util.AssetValuation
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CurrencyConverter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SearchResult(
    val title: String,
    val subtitle: String,
    val amountText: String,
    val amountColorRes: Int,
    val haystack: String
)

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val adapter = SearchAdapter()
    private var allResults: List<SearchResult> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = filter(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })

        loadData()
        binding.etSearch.requestFocus()
    }

    private fun loadData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            val results = mutableListOf<SearchResult>()

            val transactions = runCatching {
                com.shai.capitall.di.ServiceLocator.transactionRepository.observeTransactions(userId).first()
            }.getOrDefault(emptyList())
            for (tx in transactions) {
                val category = CategoryCatalog.labelFor(this@SearchActivity, tx.category)
                results += SearchResult(
                    title = tx.merchant,
                    subtitle = "${getString(R.string.search_kind_transaction)} · $category",
                    amountText = CurrencyConverter.formatIls(tx.amount),
                    amountColorRes = if (tx.amount < 0) R.color.red_negative else R.color.green_positive,
                    haystack = "${tx.merchant} ${tx.notes} $category ${tx.category}".lowercase()
                )
            }

            val assets = runCatching {
                com.shai.capitall.di.ServiceLocator.portfolioRepository.observeAssets(userId).first()
            }.getOrDefault(emptyList())
            for (asset in assets) {
                val category = CategoryCatalog.labelFor(this@SearchActivity, asset.category)
                // נכסים נסחרים (מניות + קריפטו) שמורים בדולר וחייבים המרה לשקל; שאר הנכסים משוערכים
                val value = if (asset.type == AssetType.STOCK || asset.type == AssetType.CRYPTO)
                    CurrencyConverter.usdToIls(asset.value)
                else AssetValuation.projectedAssetValue(asset)
                results += SearchResult(
                    title = asset.name,
                    subtitle = "${getString(R.string.search_kind_asset)} · $category",
                    amountText = CurrencyConverter.formatIls(value),
                    amountColorRes = R.color.green_positive,
                    haystack = "${asset.name} $category ${asset.category} ${asset.symbol.orEmpty()}".lowercase()
                )
            }

            val liabilities = runCatching {
                com.shai.capitall.di.ServiceLocator.portfolioRepository.observeLiabilities(userId).first()
            }.getOrDefault(emptyList())
            for (liability in liabilities) {
                val category = CategoryCatalog.labelFor(this@SearchActivity, liability.category)
                results += SearchResult(
                    title = liability.name,
                    subtitle = "${getString(R.string.search_kind_liability)} · $category",
                    amountText = CurrencyConverter.formatIls(AssetValuation.projectedLiabilityValue(liability)),
                    amountColorRes = R.color.red_negative,
                    haystack = "${liability.name} $category ${liability.category}".lowercase()
                )
            }

            allResults = results
            filter(binding.etSearch.text?.toString().orEmpty())
        }
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) emptyList() else allResults.filter { it.haystack.contains(q) }
        adapter.submit(filtered)

        binding.recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.text = getString(
            if (q.isBlank()) R.string.search_hint_typing else R.string.search_empty
        )
    }
}

private class SearchAdapter : RecyclerView.Adapter<SearchAdapter.VH>() {

    private val items = mutableListOf<SearchResult>()

    fun submit(list: List<SearchResult>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvResultTitle.text = item.title
        holder.binding.tvResultSubtitle.text = item.subtitle
        holder.binding.tvResultAmount.text = item.amountText
        holder.binding.tvResultAmount.setTextColor(
            holder.itemView.context.getColor(item.amountColorRes)
        )
    }

    override fun getItemCount() = items.size
}
