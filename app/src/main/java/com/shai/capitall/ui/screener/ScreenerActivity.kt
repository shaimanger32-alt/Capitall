package com.shai.capitall.ui.screener
import com.shai.capitall.util.CurrencyConverter

import android.os.Bundle
import androidx.core.view.GravityCompat
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.R
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.data.repository.CategoryRepository
import com.shai.capitall.databinding.ActivityScreenerBinding
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.playRiseAnimation
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenerBinding
    private lateinit var viewModel: ScreenerViewModel
    private lateinit var adapter: TransactionTableAdapter

    // מפתחות הקטגוריות האמיתיים (בסיס + מותאמות אישית), נטענים פעם אחת
    private var categoryKeys: List<String> = com.shai.capitall.di.ServiceLocator.categoryRepository.defaultCategories

    // העסקה שמוצגת כרגע במגירת הפירוט (לשמירה/מחיקה)
    private var selectedTx: Transaction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        viewModel = ViewModelProvider(this)[ScreenerViewModel::class.java]

        adapter = TransactionTableAdapter { tx -> openDetailDrawer(tx) }
        binding.recyclerViewTable.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewTable.adapter = adapter

        loadCategories()
        setupFilterBar()
        setupSortableHeaders()

        viewModel.uiState.observe(this) { state ->
            adapter.submitList(state.rows)
            binding.recyclerViewTable.playRiseAnimation()
            updateStatsStrip(state.count, state.totalExpense, state.averagePerTransaction)
        }
    }

    private fun loadCategories() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            categoryKeys = com.shai.capitall.di.ServiceLocator.categoryRepository.getCategoriesOnce(userId)
        }
    }

    private fun setupFilterBar() {
        binding.chipDateRange.setOnClickListener {
            val options = arrayOf(
                getString(R.string.screener_filter_date_month),
                getString(R.string.screener_filter_date_quarter),
                getString(R.string.screener_filter_date_ytd),
                getString(R.string.screener_filter_date_all)
            )
            AlertDialog.Builder(this)
                .setItems(options) { _, which ->
                    val range = when (which) {
                        0 -> DateRangeFilter.THIS_MONTH
                        1 -> DateRangeFilter.LAST_QUARTER
                        2 -> DateRangeFilter.YEAR_TO_DATE
                        else -> DateRangeFilter.ALL
                    }
                    binding.chipDateRange.text = options[which]
                    viewModel.setDateRange(range)
                }
                .show()
        }

        binding.chipCategories.setOnClickListener {
            val labels = categoryKeys.map { CategoryCatalog.labelFor(this, it) }.toTypedArray()
            val checked = BooleanArray(categoryKeys.size) { i ->
                categoryKeys[i] in viewModel.getSelectedCategories()
            }
            val selected = viewModel.getSelectedCategories().toMutableSet()

            AlertDialog.Builder(this)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    if (isChecked) selected.add(categoryKeys[which]) else selected.remove(categoryKeys[which])
                }
                .setPositiveButton(getString(R.string.add_entry_save)) { _, _ ->
                    viewModel.setSelectedCategories(selected)
                    binding.chipCategories.text = if (selected.isEmpty())
                        getString(R.string.screener_filter_categories)
                    else "${getString(R.string.screener_filter_categories)} (${selected.size})"
                }
                .setNegativeButton(getString(R.string.portfolio_delete_cancel), null)
                .show()
        }

        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = when (checkedId) {
                R.id.btnTypeExpenses -> TypeFilter.EXPENSES
                R.id.btnTypeIncome -> TypeFilter.INCOME
                else -> TypeFilter.ALL
            }
            viewModel.setTypeFilter(type)
        }

        val applyAmountRange = {
            val min = binding.etMinAmount.text.toString().toDoubleOrNull()
            val max = binding.etMaxAmount.text.toString().toDoubleOrNull()
            viewModel.setAmountRange(min, max)
        }
        binding.etMinAmount.setOnEditorActionListener { _, _, _ -> applyAmountRange(); true }
        binding.etMaxAmount.setOnEditorActionListener { _, _, _ -> applyAmountRange(); true }

        binding.tvClearAll.setOnClickListener {
            viewModel.clearAllFilters()
            binding.chipDateRange.text = getString(R.string.screener_filter_date_all)
            binding.chipCategories.text = getString(R.string.screener_filter_categories)
            binding.etMinAmount.setText("")
            binding.etMaxAmount.setText("")
            binding.toggleType.check(R.id.btnTypeAll)
        }
    }

    private fun setupSortableHeaders() {
        binding.headerDate.setOnClickListener { viewModel.sortBy(SortColumn.DATE) }
        binding.headerMerchant.setOnClickListener { viewModel.sortBy(SortColumn.MERCHANT) }
        binding.headerAmount.setOnClickListener { viewModel.sortBy(SortColumn.AMOUNT) }
    }

    private fun updateStatsStrip(count: Int, totalExpense: Double, average: Double) {
        val format = CurrencyConverter.ilsFormatter()
        binding.tvStatsStrip.text = getString(
            R.string.screener_stats_strip,
            count,
            format.format(totalExpense),
            format.format(average)
        )
    }

    private fun openDetailDrawer(tx: Transaction) {
        selectedTx = tx
        val format = CurrencyConverter.ilsFormatter()
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)

        binding.tvDetailMerchant.text = tx.merchant
        binding.tvDetailDate.text = dateFormat.format(Date(tx.timestamp))

        val isIncome = tx.amount > 0
        val sign = if (isIncome) "+" else ""
        binding.tvDetailAmount.text = "$sign${format.format(tx.amount)}"
        binding.tvDetailAmount.setTextColor(
            getColor(if (isIncome) R.color.green_positive else R.color.red_negative)
        )

        val labels = categoryKeys.map { CategoryCatalog.labelFor(this, it) }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        binding.spinnerDetailCategory.adapter = spinnerAdapter
        binding.spinnerDetailCategory.setSelection(categoryKeys.indexOf(tx.category).coerceAtLeast(0))

        binding.etDetailNotes.setText(tx.notes)
        binding.switchRecurring.isChecked = tx.isRecurring

        binding.drawerLayout.openDrawer(GravityCompat.END)

        binding.btnSaveDetail.setOnClickListener { saveDetailEdits() }
        binding.btnDeleteDetail.setOnClickListener { confirmDeleteTransaction() }
        binding.btnCloseDetail.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
        }
    }

    private fun saveDetailEdits() {
        val tx = selectedTx ?: return
        val category = categoryKeys.getOrNull(binding.spinnerDetailCategory.selectedItemPosition) ?: tx.category
        viewModel.updateTransaction(
            tx,
            category = category,
            notes = binding.etDetailNotes.text.toString(),
            isRecurring = binding.switchRecurring.isChecked
        )
        binding.drawerLayout.closeDrawer(GravityCompat.END)
    }

    private fun confirmDeleteTransaction() {
        val tx = selectedTx ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.portfolio_delete_title, tx.merchant))
            .setMessage(R.string.portfolio_delete_message)
            .setPositiveButton(R.string.portfolio_delete_confirm) { _, _ ->
                viewModel.deleteTransaction(tx)
                binding.drawerLayout.closeDrawer(GravityCompat.END)
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }
}