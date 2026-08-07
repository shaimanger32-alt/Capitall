package com.shai.capitall.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {

    private const val FINNHUB_BASE_URL = "https://finnhub.io/api/v1/"
    private const val YAHOO_BASE_URL = "https://query1.finance.yahoo.com/"
    private const val BOI_BASE_URL = "https://edge.boi.gov.il/"

    val finnhubApi: FinnhubApi by lazy {
        Retrofit.Builder()
            .baseUrl(FINNHUB_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FinnhubApi::class.java)
    }

    val yahooChartApi: YahooChartApi by lazy {
        Retrofit.Builder()
            .baseUrl(YAHOO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YahooChartApi::class.java)
    }

    /** שערי החליפין היציגים של בנק ישראל — המקור הרשמי (ללא מפתח). */
    val boiFxApi: BoiFxApi by lazy {
        Retrofit.Builder()
            .baseUrl(BOI_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BoiFxApi::class.java)
    }
}
