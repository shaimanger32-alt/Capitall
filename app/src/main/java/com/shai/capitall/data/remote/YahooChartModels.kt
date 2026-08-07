package com.shai.capitall.data.remote

data class YahooChartResponse(
    val chart: YahooChart = YahooChart()
)

data class YahooChart(
    val result: List<YahooChartResult>? = null
)

data class YahooChartResult(
    val meta: YahooChartMeta = YahooChartMeta(),
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators = YahooIndicators()
)

data class YahooChartMeta(
    val regularMarketPrice: Double = 0.0,
    val chartPreviousClose: Double = 0.0
)

data class YahooIndicators(
    val quote: List<YahooQuoteSeries>? = null
)

data class YahooQuoteSeries(
    val close: List<Double?>? = null
)
