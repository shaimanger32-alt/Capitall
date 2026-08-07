package com.shai.capitall.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.BuildConfig
import androidx.lifecycle.lifecycleScope
import com.shai.capitall.CapitallApp
import com.shai.capitall.R
import com.shai.capitall.data.repository.StockRepository
import kotlinx.coroutines.launch
import com.shai.capitall.databinding.ActivitySettingsBinding
import com.shai.capitall.ui.auth.LoginActivity
import com.shai.capitall.ui.csvimport.ImportActivity
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.util.LanguageManager
import com.shai.capitall.util.hapticTap

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val user = FirebaseAuth.getInstance().currentUser
        binding.tvUserName.text = user?.displayName ?: getString(R.string.settings_title)
        binding.tvUserEmail.text = user?.email.orEmpty()
        binding.tvVersion.text = BuildConfig.VERSION_NAME
        updateLanguageValue()
        updateCurrencyValue()

        binding.rowLanguage.setOnClickListener { showLanguagePicker() }
        binding.rowCurrency.setOnClickListener {
            it.hapticTap()
            showCurrencyPicker()
        }
        setupHideBalanceSwitch()
        binding.rowImport.setOnClickListener {
            it.hapticTap()
            startActivity(Intent(this, ImportActivity::class.java))
        }
        binding.btnSignOut.setOnClickListener { confirmSignOut() }
    }

    // ---------- מטבע תצוגה ----------

    private val currencyLabels
        get() = mapOf(
            "ILS" to getString(R.string.settings_currency_ils),
            "USD" to getString(R.string.settings_currency_usd),
            "EUR" to getString(R.string.settings_currency_eur)
        )

    private fun updateCurrencyValue() {
        binding.tvCurrencyValue.text =
            currencyLabels[CurrencyConverter.displayCurrency] ?: getString(R.string.settings_currency_ils)
    }

    private fun showCurrencyPicker() {
        val codes = CurrencyConverter.SUPPORTED_CURRENCIES.sortedBy { it != "ILS" } // ILS ראשון
        val labels = codes.map { currencyLabels[it] ?: it }.toTypedArray()
        val currentIndex = codes.indexOf(CurrencyConverter.displayCurrency).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_currency_picker_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val chosen = codes[which]
                CurrencyConverter.setDisplayCurrency(chosen)
                getSharedPreferences(CapitallApp.PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(CapitallApp.KEY_DISPLAY_CURRENCY, chosen)
                    .apply()
                updateCurrencyValue()
                // מרענן את השער החי של המטבע הנבחר, כדי שההמרה לא תשתמש בברירת המחדל הקשיחה
                if (chosen != "ILS") {
                    lifecycleScope.launch {
                        val repository = com.shai.capitall.di.ServiceLocator.stockRepository
                        if (chosen == "USD") runCatching { repository.getUsdToIlsRate() }
                        if (chosen == "EUR") runCatching { repository.getEurToIlsRate() }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    // ---------- הסתרת סכומים (מסונכרן עם כפתור העין בדשבורד — אותו מפתח העדפה) ----------

    private fun setupHideBalanceSwitch() {
        val prefs = getSharedPreferences(CapitallApp.PREFS_NAME, MODE_PRIVATE)
        binding.switchHideBalance.isChecked = prefs.getBoolean(CapitallApp.KEY_BALANCE_HIDDEN, false)
        binding.switchHideBalance.setOnCheckedChangeListener { view, isChecked ->
            view.hapticTap()
            prefs.edit().putBoolean(CapitallApp.KEY_BALANCE_HIDDEN, isChecked).apply()
        }
    }

    private fun updateLanguageValue() {
        binding.tvLanguageValue.text = when (LanguageManager.getSavedLanguage(this)) {
            LanguageManager.LANG_HEBREW -> getString(R.string.language_hebrew)
            else -> getString(R.string.language_english)
        }
    }

    private fun showLanguagePicker() {
        val languages = arrayOf(getString(R.string.language_english), getString(R.string.language_hebrew))
        val codes = arrayOf(LanguageManager.LANG_ENGLISH, LanguageManager.LANG_HEBREW)
        val currentIndex = codes.indexOf(LanguageManager.getSavedLanguage(this)).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.language_picker_title)
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                LanguageManager.setLanguage(this, codes[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_sign_out)
            .setPositiveButton(R.string.settings_sign_out) { _, _ -> signOut() }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    private fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
