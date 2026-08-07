package com.shai.capitall.data.repository

import com.shai.capitall.BuildConfig
import com.shai.capitall.data.model.StockSearchResult
import com.shai.capitall.data.remote.BoiFxApi
import com.shai.capitall.data.remote.FinnhubApi
import com.shai.capitall.data.remote.NetworkModule
import com.shai.capitall.data.remote.YahooChartApi
import com.shai.capitall.util.BoiSdmxParser
import com.shai.capitall.util.CurrencyConverter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

enum class ChartRange(val range: String, val interval: String) {
    DAY("1d", "5m"),
    WEEK("5d", "15m"),
    MONTH("1mo", "1d"),
    SIX_MONTHS("6mo", "1d"),
    YEAR("1y", "1d"),
    FIVE_YEARS("5y", "1wk"),
    MAX("max", "1mo")
}

data class PricePoint(
    val timestamp: Long, // millis
    val price: Double
)

/**
 * Cache משותף (process-wide) לנתוני שוק — מחירים, שער מט"ח והיסטוריה.
 * ה-repository נוצר אד-הוק בכל מסך, ולכן ה-cache חייב להיות ברמת object משותף
 * כדי שיחסוך קריאות רשת חוזרות (וישמור על מכסת ה-API החינמית של Finnhub).
 */
private object MarketCache {
    private const val PRICE_TTL_MS = 60_000L
    private const val FX_TTL_MS = 60_000L
    private const val HISTORY_TTL_MS = 5 * 60_000L

    private val prices = ConcurrentHashMap<String, Pair<Double, Long>>()
    private val histories = ConcurrentHashMap<String, Pair<List<PricePoint>, Long>>()
    @Volatile private var fxEntry: Pair<Double, Long>? = null
    @Volatile private var fxEurEntry: Pair<Double, Long>? = null

    private fun fresh(at: Long, ttl: Long) = System.currentTimeMillis() - at < ttl

    fun cachedPrice(symbol: String): Double? =
        prices[symbol]?.takeIf { fresh(it.second, PRICE_TTL_MS) }?.first

    fun putPrice(symbol: String, value: Double) {
        prices[symbol] = value to System.currentTimeMillis()
    }

    fun cachedFx(): Double? = fxEntry?.takeIf { fresh(it.second, FX_TTL_MS) }?.first

    fun putFx(value: Double) {
        fxEntry = value to System.currentTimeMillis()
    }

    fun cachedFxEur(): Double? = fxEurEntry?.takeIf { fresh(it.second, FX_TTL_MS) }?.first

    fun putFxEur(value: Double) {
        fxEurEntry = value to System.currentTimeMillis()
    }

    fun cachedHistory(key: String): List<PricePoint>? =
        histories[key]?.takeIf { fresh(it.second, HISTORY_TTL_MS) }?.first

    fun putHistory(key: String, value: List<PricePoint>) {
        histories[key] = value to System.currentTimeMillis()
    }
}

class StockRepository(
    private val api: FinnhubApi = NetworkModule.finnhubApi,
    private val chartApi: YahooChartApi = NetworkModule.yahooChartApi,
    private val boiApi: BoiFxApi = NetworkModule.boiFxApi
) {
    private val apiKey = BuildConfig.FINNHUB_API_KEY

    suspend fun searchSymbols(query: String): List<StockSearchResult> {
        if (query.isBlank()) return emptyList()
        return api.search(query, apiKey).result
            .filter { it.symbol.isNotBlank() && !it.symbol.contains(".") }
            .map { StockSearchResult(symbol = it.symbol, name = it.description, exchangeType = it.type) }
    }

    suspend fun getCurrentPrice(symbol: String): Double {
        MarketCache.cachedPrice(symbol)?.let { return it }
        val price = api.quote(symbol, apiKey).currentPrice
        if (price > 0) MarketCache.putPrice(symbol, price)
        return price
    }

    /**
     * מושך מחירים לכמה סמלים במקביל. עמיד לתקלות: סמל בודד שנכשל (למשל מניה
     * שנמחקה מהמסחר) פשוט מושמט מהמפה — ולא מבטל את כל הרענון.
     */
    suspend fun getCurrentPrices(symbols: List<String>): Map<String, Double> = coroutineScope {
        symbols.distinct()
            .map { symbol -> async { symbol to runCatching { getCurrentPrice(symbol) }.getOrNull() } }
            .map { it.await() }
            .mapNotNull { (symbol, price) -> if (price != null && price > 0.0) symbol to price else null }
            .toMap()
    }

    /**
     * מחיר קריפטו נוכחי (בדולר) מ-Yahoo לפי סימול כמו "BTC-USD".
     * Finnhub מוגבל בקריפטו בשכבה החינמית, ולכן קריפטו מתומחר דרך Yahoo (כמו שער המט"ח).
     */
    suspend fun getCryptoPrice(symbol: String): Double {
        MarketCache.cachedPrice(symbol)?.let { return it }
        val price = chartApi.chart(symbol, "1d", "1d")
            .chart.result?.firstOrNull()?.meta?.regularMarketPrice ?: 0.0
        if (price > 0) MarketCache.putPrice(symbol, price)
        return price
    }

    /** מחירי קריפטו לכמה סמלים במקביל, עמיד לתקלות (סמל שנכשל מושמט). */
    suspend fun getCryptoPrices(symbols: List<String>): Map<String, Double> = coroutineScope {
        symbols.distinct()
            .map { symbol -> async { symbol to runCatching { getCryptoPrice(symbol) }.getOrNull() } }
            .map { it.await() }
            .mapNotNull { (symbol, price) -> if (price != null && price > 0.0) symbol to price else null }
            .toMap()
    }

    /**
     * שער USD→ILS. המקור הראשי הוא **השער היציג של בנק ישראל** (רשמי, ללא מפתח),
     * ו-Yahoo משמש כגיבוי כשבנק ישראל אינו זמין — כך השער לעולם לא נופל לברירת המחדל.
     * מעדכן את ה-cache המשותף ב-[CurrencyConverter] ומחזיר את השער.
     * לעולם לא זורק — במקרה כשל מחזיר את השער האחרון הידוע (או ברירת המחדל).
     */
    suspend fun getUsdToIlsRate(): Double {
        MarketCache.cachedFx()?.let {
            CurrencyConverter.updateRate(it)
            return CurrencyConverter.usdToIls
        }
        val rate = fetchBoiRate(BoiFxApi.SERIES_USD_ILS)
            ?: fetchYahooRate("USDILS=X")
        if (rate != null && rate > 0) {
            MarketCache.putFx(rate)
            CurrencyConverter.updateRate(rate)
        }
        return CurrencyConverter.usdToIls
    }

    /**
     * שער EUR→ILS — אותה מדיניות: בנק ישראל ראשי, Yahoo גיבוי.
     * לעולם לא זורק — במקרה כשל מחזיר את השער האחרון הידוע (או ברירת המחדל).
     */
    suspend fun getEurToIlsRate(): Double {
        MarketCache.cachedFxEur()?.let {
            CurrencyConverter.updateEurRate(it)
            return CurrencyConverter.eurToIls
        }
        val rate = fetchBoiRate(BoiFxApi.SERIES_EUR_ILS)
            ?: fetchYahooRate("EURILS=X")
        if (rate != null && rate > 0) {
            MarketCache.putFxEur(rate)
            CurrencyConverter.updateEurRate(rate)
        }
        return CurrencyConverter.eurToIls
    }

    /** השער היציג האחרון מבנק ישראל, או null אם הקריאה/הפענוח נכשלו. */
    private suspend fun fetchBoiRate(series: String): Double? = runCatching {
        BoiSdmxParser.parseLatestRate(
            boiApi.series(series = series, lastNObservations = 1)
        )
    }.getOrNull()

    /** גיבוי: שער חי מ-Yahoo לפי סימול מט"ח (למשל "USDILS=X"). */
    private suspend fun fetchYahooRate(symbol: String): Double? = runCatching {
        chartApi.chart(symbol, "1d", "1d")
            .chart.result?.firstOrNull()?.meta?.regularMarketPrice
    }.getOrNull()

    /**
     * סדרת שער USD→ILS יומית היסטורית, לאותו טווח.
     * משמשת להמרת ערך המניות בכל תאריך בעקומת השווי הנקי לפי השער של אותו יום.
     *
     * גם כאן בנק ישראל ראשי: הסדרה היציגה היא המקור הרשמי לשער יומי. בימים ללא
     * פרסום (סופ"ש/חג) פשוט אין נקודה — ובונה העקומה כבר משלים קדימה את השער האחרון הידוע.
     */
    suspend fun getFxHistory(range: ChartRange): List<PricePoint> {
        val cacheKey = "BOI_USD_ILS|${range.name}"
        MarketCache.cachedHistory(cacheKey)?.let { return it }

        val boiPoints = runCatching {
            BoiSdmxParser.parseSeries(
                boiApi.series(
                    series = BoiFxApi.SERIES_USD_ILS,
                    startPeriod = boiStartPeriod(range)
                )
            ).map { PricePoint(it.timestamp, it.rate) }
        }.getOrNull().orEmpty()

        if (boiPoints.isNotEmpty()) {
            MarketCache.putHistory(cacheKey, boiPoints)
            return boiPoints
        }
        // גיבוי: היסטוריית המט"ח של Yahoo
        return getPriceHistory("USDILS=X", range)
    }

    /** תאריך התחלה (yyyy-MM-dd) לשאילתת בנק ישראל לפי טווח הגרף. */
    private fun boiStartPeriod(range: ChartRange): String {
        val days = when (range) {
            ChartRange.DAY -> 7L          // מרווח מינימלי — יש ימים ללא פרסום
            ChartRange.WEEK -> 21L
            ChartRange.MONTH -> 62L
            ChartRange.SIX_MONTHS -> 200L
            ChartRange.YEAR -> 400L
            ChartRange.FIVE_YEARS -> 1900L
            ChartRange.MAX -> 7300L
        }
        val start = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(start))
    }

    suspend fun getPriceHistory(symbol: String, range: ChartRange): List<PricePoint> {
        val cacheKey = "$symbol|${range.name}"
        MarketCache.cachedHistory(cacheKey)?.let { return it }

        val result = chartApi.chart(symbol, range.range, range.interval)
            .chart.result?.firstOrNull() ?: return emptyList()
        val timestamps = result.timestamp ?: return emptyList()
        val closes = result.indicators.quote?.firstOrNull()?.close ?: return emptyList()
        val points = timestamps.zip(closes)
            .mapNotNull { (ts, close) -> close?.let { PricePoint(ts * 1000L, it) } }
        if (points.isNotEmpty()) MarketCache.putHistory(cacheKey, points)
        return points
    }
}
