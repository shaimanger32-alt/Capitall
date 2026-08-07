package com.shai.capitall.ui.addtransaction

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.Chip
import com.shai.capitall.R
import com.shai.capitall.data.model.CategoryScope
import com.shai.capitall.databinding.ActivityAddTransactionBinding
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.hapticConfirm
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private lateinit var viewModel: AddTransactionViewModel
    private var selectedTimestamp: Long = System.currentTimeMillis()
    private var selectedCategoryKey: String? = null
    private var allCategories: List<String> = emptyList()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AddTransactionViewModel::class.java]

        setupPaymentMethodSpinner()
        setupDatePicker()
        setupCategoryChips()

        binding.btnSaveTransaction.setOnClickListener {
            it.hapticConfirm()
            val merchant = binding.etMerchant.text.toString()
            val amount = binding.etAmount.text.toString()
            val category = selectedCategoryKey ?: ""
            val paymentMethod = binding.spinnerPaymentMethod.selectedItem as? String ?: "Card"
            val notes = binding.etNotes.text.toString()
            val isIncome = binding.rbIncome.isChecked
            val isRecurring = binding.switchRecurring.isChecked

            viewModel.saveTransaction(
                merchant, category, amount, isIncome, paymentMethod, notes, isRecurring, selectedTimestamp
            )
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                is AddTransactionState.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.btnSaveTransaction.isEnabled = false
                }
                is AddTransactionState.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, getString(R.string.add_entry_saved_success), Toast.LENGTH_SHORT).show()
                    finish()
                }
                is AddTransactionState.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnSaveTransaction.isEnabled = true
                    Toast.makeText(this, getString(state.messageRes), Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }
    }

    private fun setupPaymentMethodSpinner() {
        val methods = listOf(
            getString(R.string.payment_card),
            getString(R.string.payment_cash),
            getString(R.string.payment_bank_transfer),
            getString(R.string.payment_direct_debit)
        )
        binding.spinnerPaymentMethod.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)
    }

    // בורר קטגוריה מבוסס צ'יפים, מסונן לפי סוג העסקה: מתג ההכנסה/הוצאה בראש המסך קובע
    // אילו קטגוריות מוצגות — הכנסה → משכורת/עסק/השקעות..., הוצאה → מזון/תחבורה/דיור...
    private fun setupCategoryChips() {
        binding.chipGroupCategory.setOnCheckedStateChangeListener { g, checkedIds ->
            selectedCategoryKey = checkedIds.firstOrNull()
                ?.let { g.findViewById<Chip>(it)?.tag as? String }
        }
        // החלפת סוג (הכנסה/הוצאה) בונה מחדש את הצ'יפים לפי הסוג הנבחר.
        // קוראים את הסוג מ-checkedId של המאזין ולא מ-isChecked של הכפתור, כי בהחלפה
        // נשלחות שתי קריאות (ביטול הישן + סימון החדש) בסדר לא מובטח.
        binding.rgType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) rebuildCategoryChips(checkedId == R.id.rbIncome)
        }
        viewModel.categories.observe(this) { categories ->
            allCategories = categories
            rebuildCategoryChips(binding.rbIncome.isChecked)
        }
    }

    private fun rebuildCategoryChips(isIncome: Boolean) {
        val scope = if (isIncome) CategoryScope.TRANSACTION_INCOME else CategoryScope.TRANSACTION_EXPENSE
        val catalogKeys = CategoryCatalog.forScope(scope).map { it.key }
        // קטגוריות מותאמות אישית (לא מהקטלוג) מוצגות בהוצאות בלבד — אין להן סוג מוגדר
        val builtInKeys = (CategoryCatalog.forScope(CategoryScope.TRANSACTION_EXPENSE) +
            CategoryCatalog.forScope(CategoryScope.TRANSACTION_INCOME)).map { it.key }
        val customKeys = allCategories.filter { it !in builtInKeys }
        val keys = if (isIncome) catalogKeys else catalogKeys + customKeys

        val group = binding.chipGroupCategory
        val previous = selectedCategoryKey
        group.removeAllViews()
        keys.forEach { group.addView(buildCategoryChip(it)) }
        if (!isIncome) group.addView(buildAddChip()) // הוספת קטגוריה מותאמת אישית — בהוצאות בלבד

        // שחזור הבחירה הקודמת אם היא רלוונטית לסוג הנוכחי, אחרת בחירת הראשונה כברירת מחדל
        val toSelect = previous?.takeIf { it in keys } ?: keys.firstOrNull()
        toSelect?.let { key ->
            (0 until group.childCount)
                .mapNotNull { group.getChildAt(it) as? Chip }
                .firstOrNull { it.tag == key }
                ?.isChecked = true
        }
    }

    private fun buildCategoryChip(key: String): Chip = Chip(this).apply {
        text = CategoryCatalog.labelFor(this@AddTransactionActivity, key)
        tag = key
        isCheckable = true
        isCheckedIconVisible = false
        chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.chip_bg_deepdive)
        setTextColor(ContextCompat.getColorStateList(context, R.color.chip_text_deepdive))
        // אייקון הקטגוריה עצמה בצ'יפ — זיהוי מהיר יותר מנקודת צבע גנרית
        chipIcon = ContextCompat.getDrawable(context, CategoryCatalog.iconFor(key))
        chipIconSize = 16f * resources.displayMetrics.density
        chipIconTint = ColorStateList.valueOf(Color.parseColor(CategoryCatalog.colorFor(key)))
    }

    private fun buildAddChip(): Chip = Chip(this).apply {
        text = getString(R.string.add_transaction_add_category_desc)
        isCheckable = false
        chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_add)
        chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gold_accent))
        setTextColor(ContextCompat.getColor(context, R.color.gray_light))
        setOnClickListener { showAddCategoryDialog() }
    }

    private fun setupDatePicker() {
        binding.etDate.setText(dateFormat.format(selectedTimestamp))
        binding.etDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    cal.set(year, month, day)
                    selectedTimestamp = cal.timeInMillis
                    binding.etDate.setText(dateFormat.format(selectedTimestamp))
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_transaction_new_category_title)
            .setView(input)
            .setPositiveButton(R.string.add_entry_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) viewModel.addCategory(name)
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }
}