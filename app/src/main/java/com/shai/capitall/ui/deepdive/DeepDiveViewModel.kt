package com.shai.capitall.ui.deepdive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.data.model.Asset
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.data.repository.ChartRange
import com.shai.capitall.data.repository.PortfolioRepository
import com.shai.capitall.data.repository.PricePoint
import com.shai.capitall.data.repository.StockRepository
import com.shai.capitall.data.repository.TransactionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

const val STOCKS_CATEGORY_KEY = "stocks_investments"

data class MonthlyPoint(val label: String, val amount: Float, val sortKey: String)
data class Contributor(val merchant: String, val amount: Double, val percent: Int)

data class DeepDiveUiState(
    val hasData: Boolean = false,
    val monthlyPoints: List<MonthlyPoint> = emptyList(),
    val budgetThreshold: Float = 0f,
    val currentMonth: Float = 0f,
    val historicalAverage: Float = 0f,
    val allTimePeak: Float = 0f,
    val contributors: List<Contributor> = emptyList(),
    val weekdayDistribution: List<Float> = List(7) { 0f },
    val weekdayThuFriSatPercent: Int = 0,
    val totalTransactions: Int = 0,         // סך העסקאות בקטגוריה (למונה בכותרת)
    val currentMonthTxCount: Int = 0,       // מספר העסקאות החודש / מספר אחזקות (תת-כותרת ל-KPI)
    val peakMonthLabel: String = "",        // החודש שבו נרשם השיא (תת-כותרת ל-KPI)
    val currentVsAvgPercent: Int = 0,       // אחוז החודש הנוכחי מול הממוצע ההיסטורי (חיובי=מעל)
    val isStockMode: Boolean = false,       // מצב ניתוח מניות (במקום ניתוח הוצאות)
    val isLoading: Boolean = false,         // בטעינה ראשונית
    val isError: Boolean = false            // כשל בטעינת הנתונים
)

class DeepDiveViewModel(
    private val category: String,
    private val transactionRepository: TransactionRepository = com.shai.capitall.di.ServiceLocator.transactionRepository,
    private val portfolioRepository: PortfolioRepository = com.shai.capitall.di.ServiceLocator.portfolioRepository,
    private val stockRepository: StockRepository = com.shai.capitall.di.ServiceLocator.stockRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val monthLabelFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
    private val isStockMode = category == STOCKS_CATEGORY_KEY

    private val _uiState = MutableLiveData(DeepDiveUiState(isStockMode = isStockMode, isLoading = true))
    val uiState: LiveData<DeepDiveUiState> = _uiState

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.value = DeepDiveUiState(isStockMode = isStockMode, isError = true)
            return
        }
        loadJob?.cancel()
        _uiState.value = DeepDiveUiState(isStockMode = isStockMode, isLoading = true)
        loadJob = viewModelScope.launch {
            if (isStockMode) {
                // ניתוח מניות: עוקב אחר אחזקות המניות ומרענן שווי חי בכל שינוי (עם הפצת שגיאה)
                portfolioRepository.observeAssetsResult(userId).collect { result ->
                    result.fold(
                        onSuccess = { recomputeStocks(it) },
                        onFailure = {
                            _uiState.value = DeepDiveUiState(isStockMode = true, isError = true)
                        }
                    )
                }
            } else {
                transactionRepository.observeTransactionsResult(userId).collect { result ->
                    result.fold(
                        onSuccess = { all ->
                            recompute(all.filter { it.category == category && it.amount < 0 })
                        },
                        onFailure = {
                            _uiState.value = DeepDiveUiState(isStockMode = false, isError = true)
                        }
                    )
                }
            }
        }
    }

    private fun recompute(transactions: List<Transaction>) {
        if (transactions.isEmpty()) {
            _uiState.value = DeepDiveUiState(hasData = false)
            return
        }

        // קיבוץ לפי חודש (yyyy-MM), ממויין כרונולוגית
        val grouped = transactions.groupBy { monthKeyFormat.format(it.timestamp) }
        val monthlyPoints = grouped.entries
            .sortedBy { it.key }
            .map { (key, txs) ->
                val amount = txs.sumOf { kotlin.math.abs(it.amount) }.toFloat()
                val label = monthLabelFormat.format(txs.first().timestamp)
                MonthlyPoint(label, amount, key)
            }

        val currentMonthKey = monthKeyFormat.format(System.currentTimeMillis())
        val currentMonth = monthlyPoints.find { it.sortKey == currentMonthKey }?.amount ?: 0f
        val historicalPoints = monthlyPoints.filter { it.sortKey != currentMonthKey }
        val historicalAverage = if (historicalPoints.isNotEmpty())
            historicalPoints.map { it.amount }.average().toFloat() else currentMonth
        val peakPoint = monthlyPoints.maxByOrNull { it.amount }
        val allTimePeak = peakPoint?.amount ?: 0f
        val budgetThreshold = monthlyPoints.map { it.amount }.average().toFloat()

        // תת-נתונים לתצוגה בכרטיס הסיכום (בהשראת מסך ה-Tracker)
        val currentMonthTxCount = transactions.count { monthKeyFormat.format(it.timestamp) == currentMonthKey }
        val currentVsAvgPercent = if (historicalAverage > 0f)
            (((currentMonth - historicalAverage) / historicalAverage) * 100).toInt() else 0

        // Top Contributors
        val totalSpend = transactions.sumOf { kotlin.math.abs(it.amount) }
        val contributors = transactions.groupBy { it.merchant }
            .map { (merchant, txs) -> merchant to txs.sumOf { kotlin.math.abs(it.amount) } }
            .sortedByDescending { it.second }
            .map { (merchant, amount) ->
                val percent = if (totalSpend > 0) ((amount / totalSpend) * 100).toInt() else 0
                Contributor(merchant, amount, percent)
            }

        // התפלגות ימי שבוע (Sun=0 ... Sat=6)
        val weekdaySums = FloatArray(7)
        val cal = Calendar.getInstance()
        transactions.forEach { tx ->
            cal.timeInMillis = tx.timestamp
            val index = cal.get(Calendar.DAY_OF_WEEK) - 1
            weekdaySums[index] += kotlin.math.abs(tx.amount).toFloat()
        }
        val weekdayTotal = weekdaySums.sum()
        val weekdayDistribution = if (weekdayTotal > 0)
            weekdaySums.map { (it / weekdayTotal) * 100f } else weekdaySums.toList()
        val thuFriSatPercent = if (weekdayTotal > 0)
            (((weekdaySums[4] + weekdaySums[5] + weekdaySums[6]) / weekdayTotal) * 100).toInt() else 0

        _uiState.value = DeepDiveUiState(
            hasData = true,
            monthlyPoints = monthlyPoints,
            budgetThreshold = budgetThreshold,
            currentMonth = currentMonth,
            historicalAverage = historicalAverage,
            allTimePeak = allTimePeak,
            contributors = contributors,
            weekdayDistribution = weekdayDistribution,
            weekdayThuFriSatPercent = thuFriSatPercent,
            totalTransactions = transactions.size,
            currentMonthTxCount = currentMonthTxCount,
            peakMonthLabel = peakPoint?.label ?: "",
            currentVsAvgPercent = currentVsAvgPercent
        )
    }

    // ---------- מצב ניתוח מניות ----------
    // מנתח את תיק המניות: מגמת שווי השוק (בשקל), אחזקות מובילות לפי שווי, ומדדי שיא/ממוצע.
    private suspend fun recomputeStocks(assets: List<Asset>) {
        val stockLots = assets.filter {
            it.type == AssetType.STOCK && !it.symbol.isNullOrBlank() && it.quantity != null
        }
        if (stockLots.isEmpty()) {
            _uiState.value = DeepDiveUiState(hasData = false, isStockMode = true)
            return
        }

        val quantities = stockLots.groupBy { it.symbol!! }
            .mapValues { (_, lots) -> lots.sumOf { it.quantity ?: 0.0 } }
        val nameBySymbol = stockLots.groupBy { it.symbol!! }
            .mapValues { (_, lots) -> lots.first().name }

        val prices = runCatching { stockRepository.getCurrentPrices(quantities.keys.toList()) }
            .getOrDefault(emptyMap())
        val fx = stockRepository.getUsdToIlsRate()

        // שווי כל אחזקה בשקל (מחיר שוק חי × כמות × שער; נפילה לעלות הקנייה אם אין מחיר)
        val holdingValue = quantities.mapValues { (symbol, qty) ->
            val price = prices[symbol]
            if (price != null && price > 0) qty * price * fx
            else stockLots.filter { it.symbol == symbol }.sumOf { it.value } * fx
        }
        val totalValue = holdingValue.values.sum()
        val contributors = holdingValue.entries
            .sortedByDescending { it.value }
            .map { (symbol, value) ->
                val label = nameBySymbol[symbol]?.ifBlank { symbol } ?: symbol
                val percent = if (totalValue > 0) ((value / totalValue) * 100).toInt() else 0
                Contributor(label, value, percent)
            }

        // סדרת שווי היסטורית (יומית → חודשית) מתוך היסטוריית המחירים
        val histories = runCatching {
            coroutineScope {
                quantities.keys.map { symbol ->
                    async { symbol to stockRepository.getPriceHistory(symbol, ChartRange.YEAR) }
                }.awaitAll().toMap()
            }
        }.getOrDefault(emptyMap())
        val monthlyPoints = buildStockMonthly(histories, quantities, fx)

        val currentMonthKey = monthKeyFormat.format(System.currentTimeMillis())
        val currentMonth = totalValue.toFloat()
        val historicalPoints = monthlyPoints.filter { it.sortKey != currentMonthKey }
        val historicalAverage = if (historicalPoints.isNotEmpty())
            historicalPoints.map { it.amount }.average().toFloat() else currentMonth
        val peakPoint = monthlyPoints.maxByOrNull { it.amount }
        val allTimePeak = maxOf(peakPoint?.amount ?: 0f, currentMonth)
        val budgetThreshold = if (monthlyPoints.isNotEmpty())
            monthlyPoints.map { it.amount }.average().toFloat() else currentMonth
        val currentVsAvgPercent = if (historicalAverage > 0f)
            (((currentMonth - historicalAverage) / historicalAverage) * 100).toInt() else 0

        _uiState.value = DeepDiveUiState(
            hasData = true,
            isStockMode = true,
            monthlyPoints = monthlyPoints,
            budgetThreshold = budgetThreshold,
            currentMonth = currentMonth,
            historicalAverage = historicalAverage,
            allTimePeak = allTimePeak,
            contributors = contributors,
            weekdayDistribution = List(7) { 0f },
            weekdayThuFriSatPercent = 0,
            totalTransactions = stockLots.size,
            currentMonthTxCount = contributors.size,   // מספר אחזקות
            peakMonthLabel = peakPoint?.label ?: "",
            currentVsAvgPercent = currentVsAvgPercent
        )
    }

    // בונה סדרת שווי-תיק חודשית (בשקל): לכל יום סוכם כמות × מחיר סגירה × שער, ולכל חודש נלקח ערך היום האחרון
    private fun buildStockMonthly(
        histories: Map<String, List<PricePoint>>,
        quantities: Map<String, Double>,
        fx: Double
    ): List<MonthlyPoint> {
        if (histories.isEmpty()) return emptyList()
        val dayMillis = 24L * 60L * 60L * 1000L
        val perSymbolDay = histories.mapValues { (_, points) ->
            points.associate { (it.timestamp / dayMillis) to it.price }
        }
        val allDays = perSymbolDay.values.flatMap { it.keys }.toSortedSet()
        if (allDays.isEmpty()) return emptyList()

        val lastKnown = HashMap<String, Double>()
        val daily = ArrayList<Pair<Long, Double>>()
        for (day in allDays) {
            var total = 0.0
            var any = false
            for ((symbol, qty) in quantities) {
                val price = perSymbolDay[symbol]?.get(day) ?: lastKnown[symbol]
                if (price != null) {
                    lastKnown[symbol] = price
                    total += qty * price * fx
                    any = true
                }
            }
            if (any) daily.add((day * dayMillis) to total)
        }

        return daily.groupBy { monthKeyFormat.format(it.first) }
            .entries.sortedBy { it.key }
            .map { (key, list) ->
                val last = list.maxByOrNull { it.first }!!
                MonthlyPoint(monthLabelFormat.format(last.first), last.second.toFloat(), key)
            }
    }
}

class DeepDiveViewModelFactory(private val category: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DeepDiveViewModel(category) as T
    }
}
