package com.shai.capitall.ui.csvimport

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.R
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.data.repository.TransactionRepository
import com.shai.capitall.util.csv.BankStatementParser
import com.shai.capitall.util.csv.CategorySource
import com.shai.capitall.util.csv.CsvReader
import com.shai.capitall.util.csv.FileSignature
import com.shai.capitall.util.csv.ImportDeduplicator
import com.shai.capitall.util.csv.ImportError
import com.shai.capitall.util.csv.ImportException
import com.shai.capitall.util.csv.ImportRow
import com.shai.capitall.util.csv.MerchantCategorizer
import com.shai.capitall.util.csv.StatementKind
import com.shai.capitall.util.pdf.PdfStatementParser
import com.shai.capitall.util.pdf.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** שורה בתצוגה המקדימה — שורת הקובץ יחד עם ההחלטות שניתן לשנות לפני היבוא. */
data class StagedRow(
    val row: ImportRow,
    val category: String,
    val source: CategorySource,
    val isDuplicate: Boolean,
    val isSelected: Boolean
)

data class PreviewData(
    val rows: List<StagedRow>,
    val kind: StatementKind,
    val duplicateCount: Int,
    val skippedCount: Int,
    /** שורות שלא זוהתה להן קטגוריה ודורשות מבט של המשתמש. */
    val needsReviewCount: Int
) {
    val selectedCount: Int get() = rows.count { it.isSelected }
    val selectedTotal: Double get() = rows.filter { it.isSelected }.sumOf { it.row.amount }
}

sealed interface ImportUiState {
    /** לפני בחירת קובץ. */
    data object Idle : ImportUiState
    data object Parsing : ImportUiState
    data class Preview(val data: PreviewData) : ImportUiState
    data object Importing : ImportUiState
    data class Done(val imported: Int) : ImportUiState
    data class Error(val messageRes: Int) : ImportUiState
}

/**
 * מנהל את זרימת היבוא: פענוח הקובץ → שיוך קטגוריות → סינון כפילויות → תצוגה מקדימה
 * לאישור → כתיבה ל-Firestore.
 *
 * כל הלוגיקה הכבדה (פענוח, שיוך, כפילויות) יושבת ב-util/csv כאובייקטים טהורים; כאן רק
 * התזמור ומצב ה-UI, כדי שהחלקים הניתנים לבדיקה יישארו ללא תלות ב-Android.
 */
class ImportViewModel(
    private val transactionRepository: TransactionRepository =
        com.shai.capitall.di.ServiceLocator.transactionRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableLiveData<ImportUiState>(ImportUiState.Idle)
    val state: LiveData<ImportUiState> = _state

    private var preview: PreviewData? = null
    private var unknownMerchantLabel: String = ""

    /**
     * מפענח את הקובץ שנבחר ובונה את התצוגה המקדימה.
     * [fallbackMerchant] מגיע מהמסך (מתורגם) כדי שה-ViewModel יישאר בלי Context.
     */
    fun parseFile(bytes: ByteArray, fallbackMerchant: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _state.value = ImportUiState.Error(R.string.add_entry_error_no_user)
            return
        }
        unknownMerchantLabel = fallbackMerchant
        _state.value = ImportUiState.Parsing

        viewModelScope.launch {
            // פענוח וקטגוריזציה על רקע — קובץ שנתי יכול להכיל אלפי שורות
            val parsed = withContext(Dispatchers.Default) {
                when (CsvReader.detectSignature(bytes)) {
                    // אקסל נבדק לפני הפענוח כדי להחזיר הודעה שאומרת מה לעשות, במקום
                    // לפענח ג'יבריש ולהתלונן על עמודות חסרות
                    FileSignature.SPREADSHEET ->
                        Result.failure(ImportException(ImportError.SPREADSHEET_FILE))
                    // דף חשבון ב-PDF: הטבלה משוחזרת מהגאומטריה של הטקסט
                    FileSignature.PDF -> runCatching { PdfTextExtractor.extract(bytes) }
                        .mapCatching { spans -> PdfStatementParser.parse(spans).getOrThrow() }
                    FileSignature.TEXT -> runCatching { CsvReader.decode(bytes) }
                        .mapCatching { text -> BankStatementParser.parse(text).getOrThrow() }
                }
            }

            val statement = parsed.getOrElse { error ->
                _state.value = ImportUiState.Error(messageResFor(error))
                return@launch
            }

            val existing = runCatching { transactionRepository.getTransactionsOnce(userId) }
                .getOrElse {
                    _state.value = ImportUiState.Error(R.string.import_error_load_history)
                    return@launch
                }

            val staged = withContext(Dispatchers.Default) {
                val categorizer = MerchantCategorizer(existing)
                val duplicates = ImportDeduplicator.findDuplicates(statement.rows, existing)
                statement.rows.mapIndexed { index, row ->
                    val categorized = categorizer.categorize(row.merchant, row.amount)
                    val isDuplicate = duplicates[index]
                    StagedRow(
                        row = row,
                        category = categorized.category,
                        source = categorized.source,
                        isDuplicate = isDuplicate,
                        // כפילויות מסומנות כלא-נבחרות כברירת מחדל, אבל נשארות גלויות
                        // כדי שהמשתמש יראה מה סוננו ויוכל לכלול אותן ידנית
                        isSelected = !isDuplicate
                    )
                }
            }

            preview = PreviewData(
                rows = staged,
                kind = statement.kind,
                duplicateCount = staged.count { it.isDuplicate },
                skippedCount = statement.skippedRows,
                needsReviewCount = staged.count { it.source == CategorySource.FALLBACK }
            )
            _state.value = ImportUiState.Preview(preview!!)
        }
    }

    fun toggleRow(index: Int) {
        updateRow(index) { it.copy(isSelected = !it.isSelected) }
    }

    fun setCategory(index: Int, category: String) {
        // בחירה ידנית של המשתמש מקבלת ביטחון מלא ומפסיקה להיספר כ"דורש בדיקה"
        updateRow(index) { it.copy(category = category, source = CategorySource.HISTORY) }
    }

    fun setAllSelected(selected: Boolean) {
        val current = preview ?: return
        // "בחר הכל" לא מחזיר כפילויות פנימה — הן סוננו מסיבה טובה
        val rows = current.rows.map {
            it.copy(isSelected = if (selected) !it.isDuplicate else false)
        }
        publish(current.copy(rows = rows))
    }

    /**
     * הופך את סימן כל השורות. פירוט אשראי מזוהה אוטומטית כהוצאות, אבל קבצים חריגים
     * (למשל דף זיכויים) עלולים להתפרש הפוך — וזה התיקון בלחיצה אחת.
     */
    fun flipSigns() {
        val current = preview ?: return
        val rows = current.rows.map { staged ->
            staged.copy(row = staged.row.copy(amount = -staged.row.amount))
        }
        publish(current.copy(rows = rows))
    }

    fun confirmImport() {
        val current = preview ?: return
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _state.value = ImportUiState.Error(R.string.add_entry_error_no_user)
            return
        }
        val selected = current.rows.filter { it.isSelected }
        if (selected.isEmpty()) {
            _state.value = ImportUiState.Error(R.string.import_error_nothing_selected)
            return
        }

        val paymentMethod = when (current.kind) {
            StatementKind.CREDIT_CARD -> PAYMENT_CARD
            StatementKind.BANK_ACCOUNT -> PAYMENT_BANK_TRANSFER
        }
        val transactions = selected.map { staged ->
            Transaction(
                userId = userId,
                merchant = staged.row.merchant.ifBlank { unknownMerchantLabel },
                category = staged.category,
                amount = staged.row.amount,
                timestamp = staged.row.timestamp,
                paymentMethod = paymentMethod,
                notes = "",
                isRecurring = false
            )
        }

        _state.value = ImportUiState.Importing
        viewModelScope.launch {
            try {
                val written = transactionRepository.addTransactions(transactions)
                _state.value = ImportUiState.Done(written)
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(R.string.import_error_save_failed)
            }
        }
    }

    private fun updateRow(index: Int, transform: (StagedRow) -> StagedRow) {
        val current = preview ?: return
        if (index !in current.rows.indices) return
        val rows = current.rows.toMutableList()
        rows[index] = transform(rows[index])
        publish(current.copy(rows = rows))
    }

    private fun publish(data: PreviewData) {
        val refreshed = data.copy(
            duplicateCount = data.rows.count { it.isDuplicate },
            needsReviewCount = data.rows.count { it.source == CategorySource.FALLBACK }
        )
        preview = refreshed
        _state.value = ImportUiState.Preview(refreshed)
    }

    private fun messageResFor(error: Throwable): Int = when ((error as? ImportException)?.error) {
        ImportError.EMPTY_FILE -> R.string.import_error_empty
        ImportError.NO_HEADER -> R.string.import_error_no_header
        ImportError.NO_ROWS -> R.string.import_error_no_rows
        ImportError.SPREADSHEET_FILE -> R.string.import_error_spreadsheet
        ImportError.PDF_LOCKED -> R.string.import_error_pdf_locked
        ImportError.PDF_UNREADABLE -> R.string.import_error_pdf_unreadable
        null -> R.string.import_error_unreadable
    }

    private companion object {
        // ערכים קנוניים התואמים לברירת המחדל במודל Transaction (לא תוויות מתורגמות)
        const val PAYMENT_CARD = "Card"
        const val PAYMENT_BANK_TRANSFER = "Bank Transfer"
    }
}
