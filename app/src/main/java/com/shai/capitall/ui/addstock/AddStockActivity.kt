package com.shai.capitall.ui.addstock
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.util.hapticConfirm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.shai.capitall.R
import com.shai.capitall.databinding.ActivityAddStockBinding
import java.text.NumberFormat
import java.util.Locale

class AddStockActivity : AppCompatActivity() {

    companion object {
        const val RESULT_SYMBOL = "result_symbol"
        const val RESULT_NAME = "result_name"
        const val RESULT_QUANTITY = "result_quantity"
        const val RESULT_PRICE = "result_price"
    }

    private lateinit var binding: ActivityAddStockBinding
    private lateinit var viewModel: AddStockViewModel
    private lateinit var adapter: StockSearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AddStockViewModel::class.java]

        adapter = StockSearchAdapter { result -> viewModel.onResultSelected(result) }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onQueryChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnChangeSelection.setOnClickListener {
            viewModel.clearSelection()
            binding.etSearch.text?.clear()
        }

        binding.btnSave.setOnClickListener {
            it.hapticConfirm()
            returnSelection()
        }

        viewModel.searchResults.observe(this) { results ->
            adapter.submitList(results)
        }

        viewModel.selectedStock.observe(this) { stock ->
            if (stock != null) {
                binding.groupSelected.visibility = View.VISIBLE
                binding.rvResults.visibility = View.GONE
                binding.tvSelectedStock.text = "${stock.symbol} — ${stock.name}"
            } else {
                binding.groupSelected.visibility = View.GONE
                binding.rvResults.visibility = View.VISIBLE
            }
        }

        viewModel.currentPrice.observe(this) { price ->
            if (price != null && price > 0) {
                val format = CurrencyConverter.usdFormatter()
                binding.tvCurrentPrice.text = getString(R.string.add_stock_current_price_label, format.format(price))
                if (binding.etPurchasePrice.text.isNullOrBlank()) {
                    binding.etPurchasePrice.setText(String.format(Locale.US, "%.2f", price))
                }
            } else {
                binding.tvCurrentPrice.text = ""
            }
        }

    }

    // מחזיר את בחירת המניה (סימול/שם/כמות/מחיר) למסך הוספת הנכס, שם נבחר תאריך הרכישה והרשומה נשמרת
    private fun returnSelection() {
        val stock = viewModel.selectedStock.value
        if (stock == null) {
            Toast.makeText(this, getString(R.string.add_stock_error_no_selection), Toast.LENGTH_SHORT).show()
            return
        }
        val quantity = binding.etQuantity.text.toString().toDoubleOrNull()
        if (quantity == null || quantity <= 0) {
            Toast.makeText(this, getString(R.string.add_entry_error_invalid_value), Toast.LENGTH_SHORT).show()
            return
        }
        val price = binding.etPurchasePrice.text.toString().toDoubleOrNull() ?: viewModel.currentPrice.value
        if (price == null || price <= 0) {
            Toast.makeText(this, getString(R.string.add_entry_error_invalid_value), Toast.LENGTH_SHORT).show()
            return
        }

        val data = Intent().apply {
            putExtra(RESULT_SYMBOL, stock.symbol)
            putExtra(RESULT_NAME, stock.name.ifBlank { stock.symbol })
            putExtra(RESULT_QUANTITY, quantity)
            putExtra(RESULT_PRICE, price)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
