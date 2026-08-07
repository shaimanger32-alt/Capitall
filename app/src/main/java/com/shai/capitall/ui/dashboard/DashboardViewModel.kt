package com.shai.capitall.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.data.model.Asset
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.model.CategoryScope
import com.shai.capitall.data.model.Liability
import com.shai.capitall.data.model.Transaction
import com.shai.capitall.data.repository.ChartRange
import com.shai.capitall.data.repository.PortfolioRepository
import com.shai.capitall.data.repository.PricePoint
import com.shai.capitall.data.repository.StockRepository
import com.shai.capitall.data.repository.TransactionRepository
import com.shai.capitall.util.AssetValuation
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.CurrencyConverter
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class DashboardUiState(
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val netWorth: Double = 0.0,
    val assets: List<Asset> = emptyList(),
    val liabilities: List<Liability> = emptyList(),
    val recurringMonthlyIncome: Double = 0.0,
    val recurringMonthlyPayments: Double = 0.0,
    val isLoading: Boolean = true,
    val isError: Boolean = false
)

data class ChartPoint(val label: String, val value: Float, val timestamp: Long)

data class CashFlowMonth(val label: String, val income: Float, val expenses: Float)

class DashboardViewModel(
    private val portfolioRepository: PortfolioRepository = com.shai.capitall.di.ServiceLocator.portfolioRepository,
    private val transactionRepository: TransactionRepository = com.shai.capitall.di.ServiceLocator.transactionRepository,
    private val stockRepository: StockRepository = com.shai.capitall.di.ServiceLocator.stockRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableLiveData(DashboardUiState())
    val uiState: LiveData<DashboardUiState> = _uiState

    private val _netWorthChartPoints = MutableLiveData<List<ChartPoint>>(emptyList())
    val netWorthChartPoints: LiveData<List<ChartPoint>> = _netWorthChartPoints

    private val _stockPortfolioChartPoints = MutableLiveData<List<ChartPoint>>(emptyList())
    val stockPortfolioChartPoints: LiveData<List<ChartPoint>> = _stockPortfolioChartPoints

    private val _transactions = MutableLiveData<List<Transaction>>(emptyList())
    val transactions: LiveData<List<Transaction>> = _transactions

    private var allTransactions: List<Transaction> = emptyList()

    private var lastAssets: List<Asset> = emptyList()
    private var lastLiabilities: List<Liability> = emptyList()
    private var lastStockSignature: String? = null
    private var stockHistories: Map<String, List<PricePoint>> = emptyMap()
    private var fxDayRate: Map<Long, Double> = emptyMap() // שער USD→ILS יומי היסטורי (מפתח = יום, ערך = שער סגירה)
    private var lastAssetsBase = 0.0        // סך שווי הנכסים המשוערך (ללא מזומן)
    private var lastLiabilitiesBase = 0.0   // סך יתרת ההתחייבויות המשוערכת

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }

    init {
        loadPortfolio()
        loadTransactionsForCategoryChart()
    }

    private var portfolioJob: Job? = null

    private fun loadPortfolio() {
        val userId = auth.currentUser?.uid ?: return
        portfolioJob?.cancel()
        portfolioJob = viewModelScope.launch {
            combine(
                portfolioRepository.observeAssetsResult(userId),
                portfolioRepository.observeLiabilitiesResult(userId)
            ) { assetsRes, liabRes -> assetsRes to liabRes }
                .collect { (assetsRes, liabRes) ->
                    if (assetsRes.isFailure || liabRes.isFailure) {
                        // שומרים את הנתונים האחרונים ומסמנים שגיאה (המסך יציג באנר "נסה שוב")
                        _uiState.value = (_uiState.value ?: DashboardUiState())
                            .copy(isLoading = false, isError = true)
                        return@collect
                    }
                    val assets = assetsRes.getOrDefault(emptyList())
                    val liabilities = liabRes.getOrDefault(emptyList())
                    lastAssets = assets
                    lastLiabilities = liabilities
                    _uiState.value = buildState(assets, liabilities)
                    loadStockPortfolioHistory(assets)
                    // מרענן את קו השווי הנקי גם בשינוי נכסים שאינם מניות (מחירי המניות נטענים בנפרד)
                    recomputeNetWorthChart()
                }
        }
    }

    /** ניסיון חוזר לטעינת התיק לאחר שגיאה. */
    fun retryPortfolio() = loadPortfolio()

    /** מרענן מחירי מניות חיים על הנתונים האחרונים — נקרא כשחוזרים למסך הדשבורד. */
    fun refreshLivePrices() {
        val assets = lastAssets
        if (assets.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = buildState(assets, lastLiabilities)
        }
        lastStockSignature = null
        loadStockPortfolioHistory(assets)
    }

    private suspend fun buildState(assets: List<Asset>, liabilities: List<Liability>): DashboardUiState {
        val adjustedAssets = applyLivePrices(assets)
        // התחייבויות מקבלות יתרה משוערת מעודכנת (ריבית שנתית מצטברת מאז הרכישה)
        val adjustedLiabilities = liabilities.map {
            it.copy(value = AssetValuation.projectedLiabilityValue(it))
        }
        lastAssetsBase = adjustedAssets.sumOf { it.value }
        lastLiabilitiesBase = adjustedLiabilities.sumOf { it.value }

        // מזומן = המאזן המצטבר של העסקאות (הכנסות פחות הוצאות) — כסף אמיתי שנספר בשווי הנקי
        val cash = allTransactions.sumOf { it.amount }
        val totalAssets = lastAssetsBase + cash
        return DashboardUiState(
            totalAssets = totalAssets,
            totalLiabilities = lastLiabilitiesBase,
            netWorth = totalAssets - lastLiabilitiesBase,
            assets = adjustedAssets,
            liabilities = adjustedLiabilities,
            recurringMonthlyIncome = adjustedAssets.sumOf { it.recurringIncomeAmount ?: 0.0 },
            recurringMonthlyPayments = adjustedLiabilities.sumOf { it.recurringPaymentAmount ?: 0.0 },
            isLoading = false
        )
    }

    // מעדכן את השווי הנקי כשמשתנות עסקאות (המזומן), בלי לטעון מחדש מחירי מניות
    private fun applyCashToState() {
        val state = _uiState.value ?: return
        val cash = allTransactions.sumOf { it.amount }
        val totalAssets = lastAssetsBase + cash
        _uiState.value = state.copy(
            totalAssets = totalAssets,
            netWorth = totalAssets - lastLiabilitiesBase
        )
    }

    // מעדכן שווי נכסים: מניות STOCK מקבלות מחיר שוק חי (כמות × מחיר נוכחי) בדולר,
    // מומרות לשקל לפי שער החליפין הנוכחי (כדי שיצטרפו נכון לשווי הנקי השקלי),
    // ושאר הנכסים מקבלים שווי משוער לפי שיעור השינוי השנתי של הקטגוריה (עליית ערך/פחת).
    private suspend fun applyLivePrices(assets: List<Asset>): List<Asset> {
        val stockSymbols = assets
            .filter { it.type == AssetType.STOCK && !it.symbol.isNullOrBlank() }
            .mapNotNull { it.symbol }.distinct()
        val cryptoSymbols = assets
            .filter { it.type == AssetType.CRYPTO && !it.symbol.isNullOrBlank() }
            .mapNotNull { it.symbol }.distinct()
        val hasMarket = stockSymbols.isNotEmpty() || cryptoSymbols.isNotEmpty()

        // מניות מתומחרות ב-Finnhub, קריפטו ב-Yahoo; שניהם מומרים לשקל לפי אותו שער
        val stockPrices = if (stockSymbols.isEmpty()) emptyMap()
        else try { stockRepository.getCurrentPrices(stockSymbols) } catch (e: Exception) { emptyMap() }
        val cryptoPrices = if (cryptoSymbols.isEmpty()) emptyMap()
        else try { stockRepository.getCryptoPrices(cryptoSymbols) } catch (e: Exception) { emptyMap() }
        val fx = if (hasMarket) stockRepository.getUsdToIlsRate() else CurrencyConverter.usdToIls

        // נכסי מט"ח נקובים בדולר/אירו — מרעננים את השערים כדי שההמרה לשקל תהיה לפי שער חי
        // ולא לפי ברירת המחדל הקשיחה. כשל אינו חוסם (השער האחרון הידוע נשמר).
        val fxCurrencies = assets.mapNotNull { it.currency?.uppercase(Locale.US) }.toSet()
        if (!hasMarket && "USD" in fxCurrencies) runCatching { stockRepository.getUsdToIlsRate() }
        if ("EUR" in fxCurrencies) runCatching { stockRepository.getEurToIlsRate() }

        return assets.map { asset ->
            val quantity = asset.quantity
            when (asset.type) {
                AssetType.STOCK, AssetType.CRYPTO -> {
                    val price = if (asset.type == AssetType.STOCK) stockPrices[asset.symbol]
                    else cryptoPrices[asset.symbol]
                    // שווי בדולר: מחיר שוק חי אם קיים, אחרת עלות הקנייה (asset.value שמור בדולר) → המרה לשקל
                    val usdValue = if (price != null && price > 0 && quantity != null) quantity * price
                    else asset.value
                    asset.copy(value = usdValue * fx)
                }
                else -> asset.copy(value = AssetValuation.projectedAssetValue(asset))
            }
        }
    }

    private fun loadStockPortfolioHistory(assets: List<Asset>) {
        // היסטוריה נמשכת לכל הנכסים הנסחרים (מניות + קריפטו) — לצורך עקומת השווי הנקי
        val marketQuantities = assets
            .filter { (it.type == AssetType.STOCK || it.type == AssetType.CRYPTO) && !it.symbol.isNullOrBlank() && it.quantity != null }
            .groupBy { it.symbol!! }
            .mapValues { (_, lots) -> lots.sumOf { it.quantity ?: 0.0 } }
        // גרף "תיק המניות" כולל מניות בלבד
        val stockQuantities = assets
            .filter { it.type == AssetType.STOCK && !it.symbol.isNullOrBlank() && it.quantity != null }
            .groupBy { it.symbol!! }
            .mapValues { (_, lots) -> lots.sumOf { it.quantity ?: 0.0 } }

        val signature = marketQuantities.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value}" }
        if (signature == lastStockSignature) return
        lastStockSignature = signature

        if (marketQuantities.isEmpty()) {
            stockHistories = emptyMap()
            fxDayRate = emptyMap()
            _stockPortfolioChartPoints.value = emptyList()
            recomputeNetWorthChart()
            return
        }
        viewModelScope.launch {
            try {
                val histories = coroutineScope {
                    marketQuantities.keys.map { symbol ->
                        async { symbol to stockRepository.getPriceHistory(symbol, ChartRange.YEAR) }
                    }.awaitAll().toMap()
                }
                stockHistories = histories
                // הגרף הייעודי מציג רק מניות (קריפטו מוצג תחת קטגוריית הקריפטו)
                _stockPortfolioChartPoints.value = combineHistories(histories, stockQuantities)
            } catch (e: Exception) {
                _stockPortfolioChartPoints.value = emptyList()
            }
            // שער דולר/שקל יומי היסטורי — לכל תאריך בעקומה נשתמש בשער הסגירה של אותו יום
            fxDayRate = try {
                stockRepository.getFxHistory(ChartRange.YEAR)
                    .associate { (it.timestamp / DAY_MILLIS) to it.price }
            } catch (e: Exception) {
                emptyMap()
            }
            recomputeNetWorthChart()
        }
    }

    // קו השווי הנקי מחושב ישירות מהנכסים/ההתחייבויות המשוערכים לכל תאריך — עקבי לחלוטין עם המספר הנוכחי:
    // לכל תאריך נסכם את שווי הנכסים שקיימים עד אז (מניות לפי מחיר שוק היסטורי, השאר לפי הצמיחה)
    // פחות ההתחייבויות (משכנתא/הלוואה מאומורטזות לאותו תאריך).
    private fun recomputeNetWorthChart() {
        _netWorthChartPoints.value = buildValueSeries(lastAssets, lastLiabilities, subtractLiabilities = true)
    }

    // בונה סדרת ערך מחושבת ישירות מהנכסים/ההתחייבויות המשוערכים לכל תאריך.
    // subtractLiabilities=true → שווי נקי (נכסים פחות התחייבויות); false → סכום קטגוריה בודדת.
    private fun buildValueSeries(
        assets: List<Asset>,
        liabilities: List<Liability>,
        subtractLiabilities: Boolean
    ): List<ChartPoint> {
        // בשווי הנקי (subtractLiabilities) המזומן מהעסקאות נכלל; בקטגוריה בודדת — לא
        val cashTx = if (subtractLiabilities) allTransactions else emptyList()
        if (assets.isEmpty() && liabilities.isEmpty() && cashTx.isEmpty()) return emptyList()

        val dayMillis = 24L * 60L * 60L * 1000L
        val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val hasStocks = assets.any { (it.type == AssetType.STOCK || it.type == AssetType.CRYPTO) && !it.symbol.isNullOrBlank() && it.quantity != null }
        val perSymbolDayPrice = stockHistories.mapValues { (_, points) ->
            points.associate { (it.timestamp / dayMillis) to it.price }
        }

        val now = System.currentTimeMillis()

        // ציר הבסיס: אם יש מניות בסדרה — הימים היומיים מהיסטוריית המחירים; אחרת — נקודות חודשיות מאז הרשומה הראשונה
        val baseAxis: List<Long> = if (hasStocks && perSymbolDayPrice.isNotEmpty()) {
            val days = perSymbolDayPrice.values.flatMap { it.keys }.toSortedSet().map { it * dayMillis }
            // מוודא שהנקודה האחרונה היא "עכשיו" — כך שקצה הגרף תואם למספר השווי הנקי הנוכחי
            if (days.isNotEmpty() && days.last() < now) days + now else days
        } else {
            val earliest = (assets.map { it.createdAt } + liabilities.map { it.createdAt } + cashTx.map { it.timestamp })
                .minOrNull() ?: return emptyList()
            monthlyAxis(earliest, now)
        }
        val axisStart = baseAxis.firstOrNull() ?: return emptyList()

        // הציר חייב לכלול גם את תאריכי האירועים עצמם — עסקאות ותאריכי רכישה של נכסים/התחייבויות.
        // בלעדיהם רשומה שתוארכה לעבר מקבלת מדרגה רק בנקודת הציר הבאה: עד חודש באיחור בציר החודשי,
        // או ישירות ב"היום" אם היא נופלת בתוך החודש הנוכחי (הבאג שבו ההכנסה קפצה להווה).
        // אירועים מוקדמים מתחילת הציר לא מתווספים — הם ממילא נספרים מצטברות בנקודה הראשונה.
        val eventTimes = (cashTx.map { it.timestamp } +
            assets.map { it.createdAt } +
            liabilities.map { it.createdAt })
            .filter { it in axisStart..now }

        val axis = (baseAxis + eventTimes).distinct().sorted()
        if (axis.isEmpty()) return emptyList()

        val lastKnownPrice = HashMap<String, Double>()
        var lastKnownFx: Double? = null
        return axis.map { t ->
            // שער דולר/שקל של אותו יום (סגירה); ליום ללא מסחר — השער האחרון הידוע; אם אין היסטוריה — השער הנוכחי
            fxDayRate[t / dayMillis]?.let { lastKnownFx = it }
            val fxForDay = lastKnownFx ?: CurrencyConverter.usdToIls
            var assetsTotal = 0.0
            for (asset in assets) {
                if (asset.createdAt > t) continue
                val symbol = asset.symbol
                val quantity = asset.quantity
                val isMarket = asset.type == AssetType.STOCK || asset.type == AssetType.CRYPTO
                assetsTotal += if (isMarket && !symbol.isNullOrBlank() && quantity != null) {
                    val price = perSymbolDayPrice[symbol]?.get(t / dayMillis)
                        ?: lastKnownPrice[symbol]
                        ?: (if (quantity != 0.0) asset.value / quantity else 0.0)
                    lastKnownPrice[symbol] = price
                    // מחיר בדולר (מניה/קריפטו) → המרה לשקל לפי שער הסגירה של אותו יום — כך העקומה משקפת גם את תנודות המט"ח
                    quantity * price * fxForDay
                } else {
                    AssetValuation.projectedAssetValue(asset, t)
                }
            }
            val liabilitiesTotal = liabilities
                .filter { it.createdAt <= t }
                .sumOf { AssetValuation.projectedLiabilityValue(it, t) }

            val total = if (subtractLiabilities) {
                // מזומן מצטבר עד אותו תאריך (הכנסות פחות הוצאות) — כך השווי הנקי עולה עם הכנסה ויורד עם הוצאה
                val cashUpTo = cashTx.filter { it.timestamp <= t }.sumOf { it.amount }
                assetsTotal - liabilitiesTotal + cashUpTo
            } else {
                assetsTotal + liabilitiesTotal
            }
            ChartPoint(dayFormat.format(Date(t)), total.toFloat(), t)
        }
    }

    private fun monthlyAxis(startMillis: Long, endMillis: Long): List<Long> {
        val points = mutableListOf<Long>()
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (cal.timeInMillis <= endMillis) {
            points.add(cal.timeInMillis)
            cal.add(Calendar.MONTH, 1)
        }
        points.add(endMillis) // מוודא שהנקודה האחרונה היא "היום" (תואם למספר הנוכחי)
        return points
    }

    // בונה סדרת שווי-תיק יומית: לכל יום סוכם כמות × מחיר סגירה על פני כל המניות,
    // עם השלמה קדימה (מחיר אחרון ידוע) לסימול שחסר לו נתון באותו יום.
    private fun combineHistories(
        histories: Map<String, List<PricePoint>>,
        quantities: Map<String, Double>
    ): List<ChartPoint> {
        val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dayMillis = 24L * 60L * 60L * 1000L

        val perSymbolDayPrice = histories.mapValues { (_, points) ->
            points.associate { (it.timestamp / dayMillis) to it.price }
        }
        val allDays = perSymbolDayPrice.values.flatMap { it.keys }.toSortedSet()
        if (allDays.isEmpty()) return emptyList()

        val lastKnown = HashMap<String, Double>()
        val result = ArrayList<ChartPoint>()
        for (day in allDays) {
            var total = 0.0
            var anyKnown = false
            for ((symbol, qty) in quantities) {
                val price = perSymbolDayPrice[symbol]?.get(day) ?: lastKnown[symbol]
                if (price != null) {
                    lastKnown[symbol] = price
                    total += qty * price
                    anyKnown = true
                }
            }
            if (anyKnown) {
                val ts = day * dayMillis
                result.add(ChartPoint(dayFormat.format(ts), total.toFloat(), ts))
            }
        }
        return result
    }

    private fun loadTransactionsForCategoryChart() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            transactionRepository.observeTransactions(userId).collect { list ->
                allTransactions = list
                _transactions.value = list
                // עסקה חדשה משנה את המזומן → מעדכן את השווי הנקי ואת הגרף
                applyCashToState()
                recomputeNetWorthChart()
            }
        }
    }

    // נקרא כשבוחרים קטגוריה בבוחר הגרף: לקטגוריית נכס/התחייבות מחזיר מגמת שווי מחושבת מהרשומות עצמן,
    // ולקטגוריית עסקה מחזיר מגמת הוצאה חודשית אמיתית מתוך העסקאות בפועל
    fun getCategoryChartPoints(category: String): List<ChartPoint> {
        val scopes = CategoryCatalog.byKey(category)?.scopes ?: emptySet()
        return if (CategoryScope.ASSET in scopes || CategoryScope.LIABILITY in scopes) {
            getAssetCategoryChartPoints(category)
        } else {
            getTransactionCategoryChartPoints(category)
        }
    }

    // מגמת שווי הקטגוריה מחושבת ישירות מהנכסים/ההתחייבויות המשוערכים (עקבי עם המספרים), לא מ-Snapshots
    private fun getAssetCategoryChartPoints(category: String): List<ChartPoint> {
        val scopes = CategoryCatalog.byKey(category)?.scopes ?: emptySet()
        val isLiability = CategoryScope.LIABILITY in scopes
        val assets = if (isLiability) emptyList() else lastAssets.filter { it.category == category }
        val liabilities = if (isLiability) lastLiabilities.filter { it.category == category } else emptyList()
        return buildValueSeries(assets, liabilities, subtractLiabilities = false)
    }

    // מגמת סך כל ההכנסות (כל עסקה עם סכום חיובי) — מצטברת לאורך זמן
    fun getTotalIncomeChartPoints(): List<ChartPoint> =
        cumulativeSeries(allTransactions.filter { it.amount > 0 }) { it.amount }

    // כולל גם הכנסות וגם הוצאות של הקטגוריה (הסכום לפי גודל מוחלט), מצטבר לאורך זמן
    private fun getTransactionCategoryChartPoints(category: String): List<ChartPoint> =
        cumulativeSeries(allTransactions.filter { it.category == category }) { abs(it.amount) }

    /**
     * בונה סדרה **מצטברת**: נקודה בתאריך המדויק של כל עסקה, כשהערך הוא הסכום שנצבר עד אותו רגע.
     *
     * קודם הסדרה חושבה כסכום חודשי בודד, ולכן חודש קטן מקודמו נראה כ*ירידה* בגרף
     * (ינואר 500 ואז מרץ 400 = "ירידה של 100") במקום כצבירה (500 → 900).
     * הסמנטיקה המצטברת גם עקבית עם קו השווי הנקי, שמוצג לצידה כקו-ייחוס.
     */
    private fun cumulativeSeries(
        transactions: List<Transaction>,
        amountOf: (Transaction) -> Double
    ): List<ChartPoint> {
        if (transactions.isEmpty()) return emptyList()
        val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val sorted = transactions.sortedBy { it.timestamp }

        var running = 0.0
        val points = ArrayList<ChartPoint>(sorted.size + 1)
        for (tx in sorted) {
            running += amountOf(tx)
            points += ChartPoint(dayFormat.format(Date(tx.timestamp)), running.toFloat(), tx.timestamp)
        }
        // מותח את הקו עד היום כדי שלא ייעצר בעסקה האחרונה (הסכום שנצבר נשאר קבוע)
        val now = System.currentTimeMillis()
        if (sorted.last().timestamp < now) {
            points += ChartPoint(dayFormat.format(Date(now)), running.toFloat(), now)
        }
        return points
    }

    // ---------- נתונים אמיתיים נגזרים מהעסקאות ----------

    // תזרים מזומנים אמיתי ל-6 החודשים האחרונים, מחושב מהעסקאות שהמשתמש הזין
    fun cashFlowLastSixMonths(): List<CashFlowMonth> {
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthLabelFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
        val buckets = LinkedHashMap<String, CashFlowMonth>()
        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -i)
            buckets[monthKeyFormat.format(cal.time)] =
                CashFlowMonth(monthLabelFormat.format(cal.time), 0f, 0f)
        }
        for (tx in allTransactions) {
            val key = monthKeyFormat.format(Date(tx.timestamp))
            val cur = buckets[key] ?: continue
            buckets[key] = if (tx.amount >= 0) {
                cur.copy(income = cur.income + tx.amount.toFloat())
            } else {
                cur.copy(expenses = cur.expenses + abs(tx.amount).toFloat())
            }
        }
        return buckets.values.toList()
    }

    fun currentMonthIncome(): Double =
        currentMonthTransactions().filter { it.amount > 0 }.sumOf { it.amount }

    fun currentMonthExpenses(): Double =
        currentMonthTransactions().filter { it.amount < 0 }.sumOf { abs(it.amount) }

    fun recentTransactions(limit: Int = 5): List<Transaction> =
        allTransactions.sortedByDescending { it.timestamp }.take(limit)

    // קטגוריית ההוצאה הגדולה ביותר החודש (מפתח + סכום), או null אם אין הוצאות
    fun topExpenseCategoryThisMonth(): Pair<String, Double>? {
        val expenses = currentMonthTransactions().filter { it.amount < 0 }
        if (expenses.isEmpty()) return null
        return expenses
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { abs(it.amount) } }
            .maxByOrNull { it.value }
            ?.toPair()
    }

    fun recurringTransactionsCount(): Int = allTransactions.count { it.isRecurring }

    private fun currentMonthTransactions(): List<Transaction> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = cal.timeInMillis
        return allTransactions.filter { it.timestamp >= startOfMonth }
    }
}