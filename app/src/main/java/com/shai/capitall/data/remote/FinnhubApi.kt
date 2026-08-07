package com.shai.capitall.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface FinnhubApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("token") token: String
    ): SymbolSearchResponse

    @GET("quote")
    suspend fun quote(
        @Query("symbol") symbol: String,
        @Query("token") token: String
    ): QuoteResponse
}
