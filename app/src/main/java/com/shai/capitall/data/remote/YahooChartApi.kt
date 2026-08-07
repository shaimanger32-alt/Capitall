package com.shai.capitall.data.remote

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface YahooChartApi {

    @Headers("User-Agent: Mozilla/5.0")
    @GET("v8/finance/chart/{symbol}")
    suspend fun chart(
        @Path("symbol") symbol: String,
        @Query("range") range: String,
        @Query("interval") interval: String
    ): YahooChartResponse
}
