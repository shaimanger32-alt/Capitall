package com.shai.capitall.ui.addasset

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.shai.capitall.R
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.model.CategoryDefinition
import com.shai.capitall.data.model.CategoryScope
import com.shai.capitall.data.repository.StockRepository
import com.shai.capitall.databinding.ActivityAddAssetBinding
import com.shai.capitall.ui.addcrypto.AddCryptoActivity
import com.shai.capitall.ui.addstock.AddStockActivity
import com.shai.capitall.util.AssetValuation
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.hapticConfirm
import com.shai.capitall.util.CurrencyConverter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private const val STOCKS_CATEGORY_KEY = "stocks_investments"
private const val CRYPTO_CATEGORY_KEY = "crypto"
private const val FOREIGN_CURRENCY_CATEGORY_KEY = "foreign_currency"

class AddAssetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddAssetBinding
    private lateinit var viewModel: AddAssetViewModel
    private var currentCategoryDefs: List<CategoryDefinition> = emptyList()
    private var selectedDateMillis: Long = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // נתוני נכס נסחר שנבחר (מניה/קריפטו); marketType != null => הרשומה תישמר כנכס נסחר
    private var pendingSymbol: String? = null
    private var pendingQuantity: Double? = null
    private var pendingMarketType: AssetType? = null

    private val addStockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { applyStockSelection(it) }
        }
    }

    private val addCryptoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { applyCryptoSelection(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddAssetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AddAssetViewModel::class.java]

        setupCurrencySpinner()
        setupCategorySpinner()
        binding.rgType.setOnCheckedChangeListener { _, _ -> populateCategorySpinner() }
        applyPreselectedCategory(intent.getStringExtra(EXTRA_CATEGORY_KEY))
        binding.btnSearchStock.setOnClickListener {
            val categoryKey = currentCategoryDefs.getOrNull(binding.spinnerCategory.selectedItemPosition)?.key
            if (categoryKey == CRYPTO_CATEGORY_KEY) {
                addCryptoLauncher.launch(Intent(this, AddCryptoActivity::class.java))
            } else {
                addStockLauncher.launch(Intent(this, AddStockActivity::class.java))
            }
        }

        binding.etDate.setText(dateFormat.format(selectedDateMillis))
        binding.etDate.setOnClickListener { showDatePicker() }

        binding.btnSave.setOnClickListener {
            it.hapticConfirm()
            val type = if (binding.rbLiability.isChecked) EntryType.LIABILITY else EntryType.ASSET
            val name = binding.etName.text.toString()
            val category = currentCategoryDefs.getOrNull(binding.spinnerCategory.selectedItemPosition)?.key ?: ""
            val value = binding.etValue.text.toString()
            val recurringAmount = if (binding.tilRecurringAmount.visibility == View.VISIBLE) {
                binding.etRecurringAmount.text.toString().toDoubleOrNull()
            } else {
                null
            }
            // כמו שאר השדות המותנים — נשמר רק כשהוא גלוי. בנכס נסחר הוא מוסתר, ולכן null.
            val annualRate = if (binding.tilAnnualRate.visibility == View.VISIBLE) {
                binding.etAnnualRate.text.toString().toDoubleOrNull()?.let { it / 100.0 }
            } else {
                null
            }
            val termYears = if (binding.tilTermYears.visibility == View.VISIBLE) {
                binding.etTermYears.text.toString().toIntOrNull()
            } else {
                null
            }
            // מטבע נשמר רק כשבורר המט"ח גלוי; לכל שאר הקטגוריות null (=שקל)
            val currency = if (binding.spinnerCurrency.visibility == View.VISIBLE) {
                CurrencyConverter.SUPPORTED_CURRENCIES
                    .getOrNull(binding.spinnerCurrency.selectedItemPosition)
            } else {
                null
            }
            viewModel.saveEntry(
                type, name, category, value, recurringAmount, selectedDateMillis,
                symbol = pendingSymbol,
                quantity = pendingQuantity,
                marketType = pendingMarketType,
                annualRate = annualRate,
                termYears = termYears,
                currency = currency
            )
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                is AddAssetState.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.btnSave.isEnabled = false
                }
                is AddAssetState.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, getString(R.string.add_entry_saved_success), Toast.LENGTH_SHORT).show()
                    finish()
                }
                is AddAssetState.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnSave.isEnabled = true
                    Toast.makeText(this, getString(state.messageRes), Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }
    }

    // ממלא את הטופס עם המניה שנבחרה; המשתמש בוחר תאריך רכישה ולוחץ שמור לסיום
    private fun applyStockSelection(data: Intent) {
        val symbol = data.getStringExtra(AddStockActivity.RESULT_SYMBOL) ?: return
        val name = data.getStringExtra(AddStockActivity.RESULT_NAME).orEmpty()
        val quantity = data.getDoubleExtra(AddStockActivity.RESULT_QUANTITY, 0.0)
        val price = data.getDoubleExtra(AddStockActivity.RESULT_PRICE, 0.0)

        pendingSymbol = symbol
        pendingQuantity = quantity
        pendingMarketType = AssetType.STOCK

        binding.etName.setText(name)
        binding.etValue.setText(String.format(Locale.US, "%.2f", quantity * price))
        Toast.makeText(this, getString(R.string.add_stock_pick_date_hint), Toast.LENGTH_LONG).show()
    }

    // ממלא את הטופס עם מטבע הקריפטו שנבחר
    private fun applyCryptoSelection(data: Intent) {
        val symbol = data.getStringExtra(AddCryptoActivity.RESULT_SYMBOL) ?: return
        val name = data.getStringExtra(AddCryptoActivity.RESULT_NAME).orEmpty()
        val quantity = data.getDoubleExtra(AddCryptoActivity.RESULT_QUANTITY, 0.0)
        val price = data.getDoubleExtra(AddCryptoActivity.RESULT_PRICE, 0.0)

        pendingSymbol = symbol
        pendingQuantity = quantity
        pendingMarketType = AssetType.CRYPTO

        binding.etName.setText(name)
        binding.etValue.setText(String.format(Locale.US, "%.2f", quantity * price))
        Toast.makeText(this, getString(R.string.add_crypto_pick_date_hint), Toast.LENGTH_LONG).show()
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.add_entry_date_hint)
            .setSelection(selectedDateMillis)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            selectedDateMillis = millis
            binding.etDate.setText(dateFormat.format(millis))
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    // מזין מראש את הקטגוריה שנבחרה במסך בורר הקטגוריות: קובע נכס/התחייבות לפי ה-scope
    // ובוחר את הקטגוריה המתאימה ב-Spinner (שינוי הרדיו מרענן את רשימת הקטגוריות).
    private fun applyPreselectedCategory(categoryKey: String?) {
        val def = categoryKey?.let { CategoryCatalog.byKey(it) } ?: return
        if (CategoryScope.LIABILITY in def.scopes) {
            binding.rbLiability.isChecked = true // מפעיל את populateCategorySpinner דרך המאזין
        }
        val index = currentCategoryDefs.indexOfFirst { it.key == def.key }
        if (index >= 0) binding.spinnerCategory.setSelection(index)
    }

    private fun setupCategorySpinner() {
        populateCategorySpinner()
        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateRecurringFieldVisibility(currentCategoryDefs.getOrNull(position))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                updateRecurringFieldVisibility(null)
            }
        }
    }

    private fun populateCategorySpinner() {
        val scope = if (binding.rbLiability.isChecked) CategoryScope.LIABILITY else CategoryScope.ASSET
        currentCategoryDefs = CategoryCatalog.forScope(scope)
        binding.spinnerCategory.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            currentCategoryDefs.map { getString(it.labelRes) }
        )
        updateRecurringFieldVisibility(currentCategoryDefs.firstOrNull())
    }

    /**
     * בורר המטבע מוצג רק בקטגוריית המט"ח. בכל קטגוריה אחרת הוא מוסתר,
     * ולכן `currency` נשמר null והנכס נחשב שקלי — בדיוק כמו כל הנכסים הישנים.
     */
    private fun updateCurrencyFieldVisibility(categoryDef: CategoryDefinition?) {
        val isForeignCurrency = categoryDef?.key == FOREIGN_CURRENCY_CATEGORY_KEY
        val visibility = if (isForeignCurrency) View.VISIBLE else View.GONE
        binding.tvCurrencyLabel.visibility = visibility
        binding.spinnerCurrency.visibility = visibility
        binding.tvCurrencyConversion.visibility = visibility

        if (!isForeignCurrency) {
            binding.tilValue.hint = getString(R.string.add_entry_value_hint)
            return
        }
        refreshCurrencyConversionPreview()
    }

    /** מציג תצוגה מקדימה חיה: כמה שקלים שווה הסכום שהוקלד, לפי השער הנוכחי. */
    private fun refreshCurrencyConversionPreview() {
        val currency = CurrencyConverter.SUPPORTED_CURRENCIES
            .getOrNull(binding.spinnerCurrency.selectedItemPosition) ?: return
        binding.tilValue.hint = getString(R.string.add_entry_value_hint_currency, currency)

        val amount = binding.etValue.text.toString().toDoubleOrNull()
        if (amount == null || currency == "ILS") {
            binding.tvCurrencyConversion.visibility =
                if (currency == "ILS") View.GONE else View.VISIBLE
            binding.tvCurrencyConversion.text = ""
            return
        }
        val rate = CurrencyConverter.rateFor(currency)
        binding.tvCurrencyConversion.visibility = View.VISIBLE
        binding.tvCurrencyConversion.text = getString(
            R.string.add_entry_currency_conversion,
            CurrencyConverter.formatIls(CurrencyConverter.toIls(amount, currency)),
            String.format(Locale.US, "%.3f", rate)
        )
    }

    private fun setupCurrencySpinner() {
        binding.spinnerCurrency.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            CurrencyConverter.SUPPORTED_CURRENCIES
        )
        binding.spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshCurrencyConversionPreview()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.etValue.addTextChangedListener(
            afterTextChanged = {
                if (binding.spinnerCurrency.visibility == View.VISIBLE) refreshCurrencyConversionPreview()
            }
        )

        // מושכים שערים חיים ברקע כדי שהתצוגה המקדימה וההמרה בשמירה יהיו מעודכנות.
        // כשל אינו חוסם — CurrencyConverter נשאר עם השער האחרון הידוע/ברירת המחדל.
        lifecycleScope.launch {
            val repository = com.shai.capitall.di.ServiceLocator.stockRepository
            runCatching { repository.getUsdToIlsRate() }
            runCatching { repository.getEurToIlsRate() }
            if (binding.spinnerCurrency.visibility == View.VISIBLE) refreshCurrencyConversionPreview()
        }
    }

    private fun updateRecurringFieldVisibility(categoryDef: CategoryDefinition?) {
        val recurringLabelRes = categoryDef?.recurringOptionLabelRes
        if (recurringLabelRes != null) {
            binding.tilRecurringAmount.hint = getString(recurringLabelRes)
            binding.tilRecurringAmount.visibility = View.VISIBLE
        } else {
            binding.tilRecurringAmount.visibility = View.GONE
            binding.etRecurringAmount.text?.clear()
        }
        val isStockCat = categoryDef?.key == STOCKS_CATEGORY_KEY
        val isCryptoCat = categoryDef?.key == CRYPTO_CATEGORY_KEY
        binding.btnSearchStock.visibility = if (isStockCat || isCryptoCat) View.VISIBLE else View.GONE
        binding.btnSearchStock.text = getString(
            if (isCryptoCat) R.string.add_crypto_search_button else R.string.add_entry_search_stock_button
        )
        // שומרים בחירת נכס נסחר רק אם היא תואמת לקטגוריה הנוכחית; אחרת מנקים
        val matchesPending = (isStockCat && pendingMarketType == AssetType.STOCK) ||
            (isCryptoCat && pendingMarketType == AssetType.CRYPTO)
        if (!matchesPending) {
            pendingSymbol = null
            pendingQuantity = null
            pendingMarketType = null
        }

        updateRateFieldForCategory(categoryDef)
        updateCurrencyFieldVisibility(categoryDef)

        // שדה תקופת ההלוואה מוצג רק להתחייבויות סילוקין (משכנתא/הלוואה/הלוואת סטודנט)
        val showTerm = binding.rbLiability.isChecked && categoryDef != null &&
            AssetValuation.isAmortizing(categoryDef.key)
        binding.tilTermYears.visibility = if (showTerm) View.VISIBLE else View.GONE
    }

    /**
     * ממלא את שדה השיעור השנתי בברירת המחדל של הקטגוריה (המשתמש יכול לשנות).
     *
     * בנכס נסחר (מניה/קריפטו) השדה מוסתר: השווי שלו נקבע ממחיר שוק חי ולא משערוך לפי זמן —
     * ראה [AssetValuation], שממנו נכסים מסוג STOCK/CRYPTO אינם עוברים כלל. הצגת שיעור שנתי שם
     * מטעה, כי הוא נראה כאילו הוא משפיע על השווי בזמן שהוא מתעלם ממנו לחלוטין.
     */
    private fun updateRateFieldForCategory(categoryDef: CategoryDefinition?) {
        val key = categoryDef?.key
        val isLiability = binding.rbLiability.isChecked
        val isMarketAsset = !isLiability && (key == STOCKS_CATEGORY_KEY || key == CRYPTO_CATEGORY_KEY)

        binding.tilAnnualRate.visibility = if (isMarketAsset) View.GONE else View.VISIBLE
        if (isMarketAsset) {
            binding.etAnnualRate.text?.clear()
            return
        }
        // אין קטגוריה נבחרת (רשימה ריקה) — השדה גלוי אך אין ברירת מחדל למלא
        if (key == null) return

        val defaultRate = if (isLiability) {
            AssetValuation.liabilityAnnualRate(key)
        } else {
            AssetValuation.assetAnnualRate(key)
        }
        binding.etAnnualRate.setText(formatRate(defaultRate * 100))
    }

    private fun formatRate(percent: Double): String =
        if (percent == percent.toLong().toDouble()) {
            percent.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", percent)
        }

    companion object {
        // מפתח הקטגוריה שנבחרה במסך בורר הקטגוריות (SelectCategoryActivity)
        const val EXTRA_CATEGORY_KEY = "extra_category_key"
    }
}