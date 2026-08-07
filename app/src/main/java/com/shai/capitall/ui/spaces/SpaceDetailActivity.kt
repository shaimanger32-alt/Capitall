package com.shai.capitall.ui.spaces

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.shai.capitall.R
import com.shai.capitall.data.model.CategoryScope
import com.shai.capitall.data.model.Space
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.databinding.ActivitySpaceDetailBinding
import com.shai.capitall.databinding.DialogSpaceTransactionBinding
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.util.bindEmptyState
import com.shai.capitall.util.hapticConfirm
import com.shai.capitall.util.hapticTap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * תיק משותף אחד: הפנקס של כל החברים, ומאזן "מי חייב למי".
 */
class SpaceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpaceDetailBinding
    private lateinit var viewModel: SpaceDetailViewModel

    private val balanceAdapter by lazy { SpaceBalanceAdapter(null) }
    private val transactionAdapter by lazy {
        SpaceTransactionAdapter(null) { tx -> confirmDeleteTransaction(tx) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpaceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val spaceId = intent.getStringExtra(EXTRA_SPACE_ID).orEmpty()
        if (spaceId.isBlank()) { finish(); return }

        viewModel = ViewModelProvider(this, SpaceDetailViewModelFactory(spaceId))[
            SpaceDetailViewModel::class.java
        ]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvBalances.layoutManager = LinearLayoutManager(this)
        binding.rvBalances.adapter = balanceAdapter
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = transactionAdapter

        binding.emptyState.root.bindEmptyState(
            titleRes = R.string.space_empty_title,
            bodyRes = R.string.space_empty_body,
            actionRes = R.string.space_add_transaction,
            onAction = { showAddDialog() }
        )

        binding.fabAddSpaceTransaction.setOnClickListener {
            it.hapticTap()
            showAddDialog()
        }

        viewModel.uiState.observe(this) { render(it) }
        viewModel.closed.observe(this) { closed ->
            if (closed) {
                Toast.makeText(this, R.string.space_no_longer_member, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // ---------- תצוגה ----------

    private fun render(state: SpaceDetailUiState) {
        val space = state.space
        binding.toolbar.title = space?.name.orEmpty()

        val format = CurrencyConverter.ilsFormatterCompact()
        binding.tvTotalExpenses.text = format.format(state.totalExpenses)
        binding.tvTotalIncome.text = format.format(state.totalIncome)

        balanceAdapter.submit(space, state.balances)
        transactionAdapter.submit(space, state.transactions)

        binding.emptyState.root.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
        binding.rvTransactions.visibility = if (state.isEmpty) View.GONE else View.VISIBLE

        renderSettlements(state, space)
    }

    /** "מי מעביר למי" — מוצג רק כשיש בפועל חוב לסגור. */
    private fun renderSettlements(state: SpaceDetailUiState, space: Space?) {
        if (state.settlements.isEmpty()) {
            binding.tvSettlements.visibility = View.GONE
            return
        }
        val format = CurrencyConverter.ilsFormatterCompact()
        val bidi = BidiFormatter.getInstance()
        binding.tvSettlements.text = state.settlements.joinToString("\n") { settlement ->
            getString(
                R.string.space_settlement_line,
                space?.nameOf(settlement.fromUserId).orEmpty(),
                space?.nameOf(settlement.toUserId).orEmpty(),
                bidi.unicodeWrap(format.format(settlement.amount), TextDirectionHeuristicsCompat.LTR)
            )
        }
        binding.tvSettlements.visibility = View.VISIBLE
    }

    // ---------- תפריט ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_space_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_delete_space)?.isVisible = viewModel.isOwner()
        menu.findItem(R.id.action_leave_space)?.isVisible = !viewModel.isOwner()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_share_code -> { shareInviteCode(); true }
        R.id.action_leave_space -> { confirmLeave(); true }
        R.id.action_delete_space -> { confirmDeleteSpace(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun shareInviteCode() {
        val space = viewModel.uiState.value?.space ?: return
        InviteCodeDialog.show(this, space.name, space.inviteCode)
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle(R.string.space_leave)
            .setMessage(R.string.space_leave_body)
            .setPositiveButton(R.string.space_leave) { _, _ ->
                viewModel.leaveSpace { success ->
                    if (success) finish()
                    else Toast.makeText(this, R.string.space_action_failed, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    private fun confirmDeleteSpace() {
        AlertDialog.Builder(this)
            .setTitle(R.string.space_delete)
            .setMessage(R.string.space_delete_body)
            .setPositiveButton(R.string.space_delete) { _, _ ->
                viewModel.deleteSpace { success ->
                    if (success) finish()
                    else Toast.makeText(this, R.string.space_action_failed, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    private fun confirmDeleteTransaction(tx: Transaction) {
        AlertDialog.Builder(this)
            .setTitle(R.string.space_delete_transaction)
            .setMessage(getString(R.string.space_delete_transaction_body, tx.merchant))
            .setPositiveButton(R.string.space_delete_transaction) { _, _ ->
                viewModel.deleteTransaction(tx.id)
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    // ---------- הוספת עסקה ----------

    private fun showAddDialog() {
        val space = viewModel.uiState.value?.space ?: return
        val form = DialogSpaceTransactionBinding.inflate(layoutInflater)

        var isIncome = false
        form.toggleType.check(R.id.btnExpense)
        form.toggleType.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                isIncome = checkedId == R.id.btnIncome
                bindCategories(form.spinnerCategory, isIncome)
            }
        }
        bindCategories(form.spinnerCategory, isIncome = false)

        // ברירת המחדל למשלם היא המשתמש הנוכחי — המקרה הנפוץ ביותר
        val payerIds = space.memberIds
        form.spinnerPayer.adapter = simpleAdapter(payerIds.map { space.nameOf(it) })
        form.spinnerPayer.setSelection(payerIds.indexOf(viewModel.currentUserId).coerceAtLeast(0))

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance()
        form.tvDate.text = dateFormat.format(calendar.time)
        form.tvDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    form.tvDate.text = dateFormat.format(calendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.space_add_transaction)
            .setView(form.root)
            .setPositiveButton(R.string.space_save, null)
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .create()
            .apply {
                // הכפתור נתפס ידנית כדי שהדיאלוג לא ייסגר כשהאימות נכשל
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val categories = CategoryCatalog.forScope(
                            if (isIncome) CategoryScope.TRANSACTION_INCOME
                            else CategoryScope.TRANSACTION_EXPENSE
                        )
                        val categoryKey = categories
                            .getOrNull(form.spinnerCategory.selectedItemPosition)?.key.orEmpty()

                        viewModel.addTransaction(
                            merchant = form.etMerchant.text.toString(),
                            category = categoryKey,
                            amountText = form.etAmount.text.toString(),
                            isIncome = isIncome,
                            payerId = payerIds.getOrNull(form.spinnerPayer.selectedItemPosition)
                                ?: viewModel.currentUserId,
                            timestamp = calendar.timeInMillis
                        ) { errorRes ->
                            if (errorRes == null) {
                                binding.root.hapticConfirm()
                                dismiss()
                            } else {
                                Toast.makeText(this@SpaceDetailActivity, errorRes, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                show()
            }
    }

    private fun bindCategories(spinner: Spinner, isIncome: Boolean) {
        val scope = if (isIncome) CategoryScope.TRANSACTION_INCOME else CategoryScope.TRANSACTION_EXPENSE
        spinner.adapter = simpleAdapter(
            CategoryCatalog.forScope(scope).map { getString(it.labelRes) }
        )
    }

    private fun simpleAdapter(labels: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    companion object {
        const val EXTRA_SPACE_ID = "space_id"
    }
}
