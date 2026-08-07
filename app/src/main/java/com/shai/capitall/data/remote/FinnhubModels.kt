package com.shai.capitall.data.remote

import com.google.gson.annotations.SerializedName

data class SymbolSearchResponse(
    val count: Int = 0,
    val result: List<SymbolMatch> = emptyList()
)

data class SymbolMatch(
    val description: String = "",
    val displaySymbol: String = "",
    val symbol: String = "",
    val type: String = ""
)

data class QuoteResponse(
    @SerializedName("c") val currentPrice: Double = 0.0,
    @SerializedName("pc") val previousClose: Double = 0.0
)
