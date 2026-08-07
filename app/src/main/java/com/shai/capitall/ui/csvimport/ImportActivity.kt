package com.shai.capitall.ui.csvimport

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shai.capitall.R
import com.shai.capitall.data.model.CategoryScope
import com.shai.capitall.databinding.ActivityImportBinding
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CurrencyConverter
import com.shai.capitall.util.hapticConfirm
import com.shai.capitall.util.hapticTap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * מסך יבוא עסקאות מקובץ CSV של בנק או חברת אשראי.
 *
 * הזרימה: בחירת קובץ (SAF — בלי הרשאת אחסון) → פענוח ושיוך קטגוריות אוטומטי →
 * תצוגה מקדימה שבה אפשר לתקן קטגוריה ולבטל שורות → כתיבה ל-Firestore.
 */
class ImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding
    private lateinit var viewModel: ImportViewModel

    /** התצוגה המקדימה האחרונה — כדי ששגיאת שמירה תחזיר את המשתמש אליה ולא תמחק את עבודתו. */
    private var lastPreview: PreviewData? = null

    private val adapter by lazy {
        ImportPreviewAdapter(
            onToggle = { index ->
                binding.rvRows.hapticTap()
                viewModel.toggleRow(index)
            },
            onCategoryClick = { index -> showCategoryPicker(index) }
        )
    }

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { readAndParse(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ImportViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvRows.layoutManager = LinearLayoutManager(this)
        binding.rvRows.adapter = adapter

        binding.btnPickFile.setOnClickListener {
            it.hapticTap()
            // קבצי יצוא מהבנקים מגיעים עם סוגי MIME לא עקביים (ולעיתים שגויים לגמרי,
            // כמו application/vnd.ms-excel לקובץ CSV), ולכן הבורר פתוח לכל הסוגים.
            pickFile.launch(arrayOf("*/*"))
        }
        binding.btnSelectAll.setOnClickListener {
            it.hapticTap()
            viewModel.setAllSelected(true)
        }
        binding.btnSelectNone.setOnClickListener {
            it.hapticTap()
            viewModel.setAllSelected(false)
        }
        binding.btnFlipSigns.setOnClickListener {
            it.hapticTap()
            viewModel.flipSigns()
        }
        binding.btnConfirmImport.setOnClickListener {
            it.hapticConfirm()
            viewModel.confirmImport()
        }

        viewModel.state.observe(this) { render(it) }
    }

    // ---------- קריאת הקובץ ----------

    private fun readAndParse(uri: Uri) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        // תקרה שמונעת OOM על קובץ ענק שנבחר בטעות
                        val limited = stream.readBytes()
                        if (limited.size > MAX_FILE_BYTES) null else limited
                    }
                }.getOrNull()
            }
            when {
                bytes == null -> toast(getString(R.string.import_error_unreadable))
                bytes.isEmpty() -> toast(getString(R.string.import_error_empty))
                else -> viewModel.parseFile(bytes, getString(R.string.import_unknown_merchant))
            }
        }
    }

    // ---------- תצוגה ----------

    private fun render(state: ImportUiState) {
        val isBusy = state is ImportUiState.Parsing || state is ImportUiState.Importing
        binding.groupLoading.visibility = if (isBusy) View.VISIBLE else View.GONE

        // בשגיאה נשארים בתצוגה המקדימה אם כבר יש כזו — כשל רשת בשמירה לא אמור למחוק
        // את הסימונים והתיקונים שהמשתמש עשה. רק אם אין תצוגה חוזרים למסך הפתיחה.
        val showPreview = state is ImportUiState.Preview ||
            (state is ImportUiState.Error && lastPreview != null)
        binding.groupPreview.visibility = if (showPreview) View.VISIBLE else View.GONE
        binding.groupIdle.visibility =
            if (state is ImportUiState.Idle || (state is ImportUiState.Error && lastPreview == null)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        when (state) {
            is ImportUiState.Idle -> Unit

            is ImportUiState.Parsing ->
                binding.tvLoadingLabel.text = getString(R.string.import_parsing)

            is ImportUiState.Importing ->
                binding.tvLoadingLabel.text = getString(R.string.import_saving)

            is ImportUiState.Preview -> {
                lastPreview = state.data
                renderPreview(state.data)
            }

            is ImportUiState.Done -> {
                toast(resources.getQuantityString(R.plurals.import_done, state.imported, state.imported))
                setResult(RESULT_OK)
                finish()
            }

            is ImportUiState.Error -> {
                lastPreview?.let { renderPreview(it) }
                showErrorDialog(getString(state.messageRes))
            }
        }
    }

    private fun renderPreview(data: PreviewData) {
        adapter.submitList(data.rows)

        binding.tvSummary.text = getString(
            R.string.import_summary_selected, data.selectedCount, data.rows.size
        )

        // פירוט רק על מה שרלוונטי — שורות שסוננו, שורות שדורשות בדיקה, וסך הסכום
        val details = buildList {
            if (data.duplicateCount > 0) {
                add(getString(R.string.import_summary_duplicates, data.duplicateCount))
            }
            if (data.needsReviewCount > 0) {
                add(getString(R.string.import_summary_review, data.needsReviewCount))
            }
            if (data.skippedCount > 0) {
                add(getString(R.string.import_summary_skipped, data.skippedCount))
            }
            add(
                getString(
                    R.string.import_summary_total,
                    CurrencyConverter.formatIls(data.selectedTotal)
                )
            )
        }
        binding.tvSummaryDetail.text = details.joinToString(" · ")

        binding.btnConfirmImport.isEnabled = data.selectedCount > 0
        binding.btnConfirmImport.text = resources.getQuantityString(
            R.plurals.import_confirm_button, data.selectedCount, data.selectedCount
        )
    }

    private fun showCategoryPicker(index: Int) {
        val state = viewModel.state.value as? ImportUiState.Preview ?: return
        val staged = state.data.rows.getOrNull(index) ?: return

        // מציגים רק קטגוריות שתואמות לכיוון התנועה — הכנסה לא אמורה לקבל קטגוריית הוצאה
        val scope = if (staged.row.amount > 0) {
            CategoryScope.TRANSACTION_INCOME
        } else {
            CategoryScope.TRANSACTION_EXPENSE
        }
        val categories = CategoryCatalog.forScope(scope)
        val labels = categories.map { getString(it.labelRes) }.toTypedArray()
        val currentIndex = categories.indexOfFirst { it.key == staged.category }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.import_category_picker_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                viewModel.setCategory(index, categories[which].key)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val MAX_FILE_BYTES = 10 * 1024 * 1024
    }
}
