package com.shai.capitall.ui.categorydetail

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.shai.capitall.R
import com.shai.capitall.databinding.ActivityCategoryDetailBinding
import com.shai.capitall.ui.portfolio.PortfolioAdapter
import com.shai.capitall.ui.portfolio.PortfolioRow
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.playRiseAnimation
import com.shai.capitall.util.bindEmptyState

class CategoryDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_IS_ASSET = "extra_is_asset"
    }

    private lateinit var binding: ActivityCategoryDetailBinding
    private lateinit var viewModel: CategoryDetailViewModel
    private lateinit var adapter: PortfolioAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: return finish()
        val isAsset = intent.getBooleanExtra(EXTRA_IS_ASSET, true)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        supportActionBar?.title = CategoryCatalog.labelFor(this, category)

        viewModel = ViewModelProvider(
            this,
            CategoryDetailViewModelFactory(category, isAsset)
        )[CategoryDetailViewModel::class.java]

        adapter = PortfolioAdapter { row -> confirmDelete(row) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.tvEmptyState.root.bindEmptyState(
            titleRes = R.string.empty_category_title,
            bodyRes = R.string.empty_category_body
        )

        viewModel.rows.observe(this) { rows ->
            adapter.submitList(rows)
            binding.recyclerView.playRiseAnimation()
            binding.tvEmptyState.root.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isError.observe(this) { isError ->
            if (isError) showLoadError() else dismissLoadError()
        }
    }

    private var errorSnackbar: com.google.android.material.snackbar.Snackbar? = null

    private fun showLoadError() {
        if (errorSnackbar?.isShown == true) return
        errorSnackbar = com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            R.string.error_load_failed,
            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.action_retry) { viewModel.retry() }
        errorSnackbar?.show()
    }

    private fun dismissLoadError() {
        errorSnackbar?.dismiss()
        errorSnackbar = null
    }

    private fun confirmDelete(row: PortfolioRow) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.portfolio_delete_title, row.name))
            .setMessage(R.string.portfolio_delete_message)
            .setPositiveButton(R.string.portfolio_delete_confirm) { _, _ -> viewModel.deleteRow(row) }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }
}
