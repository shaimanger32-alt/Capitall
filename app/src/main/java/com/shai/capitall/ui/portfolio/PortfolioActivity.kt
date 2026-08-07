package com.shai.capitall.ui.portfolio

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.shai.capitall.R
import com.shai.capitall.databinding.ActivityPortfolioBinding
import com.shai.capitall.ui.categorydetail.CategoryDetailActivity
import com.shai.capitall.ui.selectcategory.SelectCategoryActivity
import com.shai.capitall.ui.stockportfolio.StockPortfolioActivity
import com.shai.capitall.util.UiState
import com.shai.capitall.util.bindEmptyState
import com.shai.capitall.util.playRiseAnimation
import com.shai.capitall.util.startPulse

private const val STOCKS_CATEGORY_KEY = "stocks_investments"
private const val CRYPTO_CATEGORY_KEY = "crypto"

class PortfolioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPortfolioBinding
    private lateinit var viewModel: PortfolioViewModel
    private lateinit var assetAdapter: CategorySummaryAdapter
    private lateinit var liabilityAdapter: CategorySummaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortfolioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        viewModel = ViewModelProvider(this)[PortfolioViewModel::class.java]

        assetAdapter = CategorySummaryAdapter { row -> onCategoryClicked(row) }
        binding.rvAssetCategories.layoutManager = LinearLayoutManager(this)
        binding.rvAssetCategories.adapter = assetAdapter
        binding.rvAssetCategories.isNestedScrollingEnabled = false

        liabilityAdapter = CategorySummaryAdapter { row -> onCategoryClicked(row) }
        binding.rvLiabilityCategories.layoutManager = LinearLayoutManager(this)
        binding.rvLiabilityCategories.adapter = liabilityAdapter
        binding.rvLiabilityCategories.isNestedScrollingEnabled = false

        binding.btnRetry.setOnClickListener { viewModel.load() }

        // מצב ריק מונחה-פעולה: מסביר מה חסר ומוביל ישירות להוספת רשומה
        binding.tvEmptyState.root.bindEmptyState(
            titleRes = R.string.empty_portfolio_title,
            bodyRes = R.string.empty_portfolio_body,
            actionRes = R.string.empty_portfolio_action,
            onAction = { startActivity(Intent(this, SelectCategoryActivity::class.java)) }
        )

        viewModel.uiState.observe(this) { state -> render(state) }
    }

    private fun render(state: UiState<PortfolioData>) {
        // skeleton במצב טעינה, עם פעימה עדינה שמסמנת שהתוכן בדרך
        val loading = state is UiState.Loading
        binding.progressBar.root.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.progressBar.root.startPulse()
        binding.errorState.visibility = if (state is UiState.Error) View.VISIBLE else View.GONE
        binding.tvEmptyState.root.visibility = if (state is UiState.Empty) View.VISIBLE else View.GONE
        binding.contentScroll.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE

        if (state is UiState.Success) {
            assetAdapter.submitList(state.data.assetGroups)
            liabilityAdapter.submitList(state.data.liabilityGroups)
            binding.rvAssetCategories.playRiseAnimation()
            binding.rvLiabilityCategories.playRiseAnimation()
        }
    }

    private fun onCategoryClicked(row: CategorySummaryRow) {
        if (row.isAsset && row.categoryKey == STOCKS_CATEGORY_KEY) {
            startActivity(Intent(this, StockPortfolioActivity::class.java))
        } else if (row.isAsset && row.categoryKey == CRYPTO_CATEGORY_KEY) {
            startActivity(
                Intent(this, StockPortfolioActivity::class.java)
                    .putExtra(StockPortfolioActivity.EXTRA_MARKET_TYPE, com.shai.capitall.data.model.AssetType.CRYPTO.name)
            )
        } else {
            val intent = Intent(this, CategoryDetailActivity::class.java)
                .putExtra(CategoryDetailActivity.EXTRA_CATEGORY, row.categoryKey)
                .putExtra(CategoryDetailActivity.EXTRA_IS_ASSET, row.isAsset)
            startActivity(intent)
        }
    }

}
