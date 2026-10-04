package com.siddharth.stockticker.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** `/v7/finance/quote?symbols=A,B,C` — batched quotes plus fundamentals. */
@JsonClass(generateAdapter = true)
data class QuoteResponse(
    @Json(name = "quoteResponse") val quoteResponse: QuoteBody? = null
)

@JsonClass(generateAdapter = true)
data class QuoteBody(
    val result: List<QuoteDto>? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class QuoteDto(
    val symbol: String? = null,
    @Json(name = "longName") val longName: String? = null,
    @Json(name = "shortName") val shortName: String? = null,
    @Json(name = "displayName") val displayName: String? = null,
    val currency: String? = null,
    @Json(name = "fullExchangeName") val fullExchangeName: String? = null,
    @Json(name = "regularMarketPrice") val regularMarketPrice: Double? = null,
    @Json(name = "regularMarketPreviousClose") val regularMarketPreviousClose: Double? = null,
    @Json(name = "regularMarketChange") val regularMarketChange: Double? = null,
    @Json(name = "regularMarketChangePercent") val regularMarketChangePercent: Double? = null,
    @Json(name = "regularMarketDayHigh") val regularMarketDayHigh: Double? = null,
    @Json(name = "regularMarketDayLow") val regularMarketDayLow: Double? = null,
    @Json(name = "regularMarketVolume") val regularMarketVolume: Long? = null,
    @Json(name = "marketCap") val marketCap: Long? = null,
    @Json(name = "trailingPE") val trailingPE: Double? = null,
    @Json(name = "trailingAnnualDividendYield") val trailingAnnualDividendYield: Double? = null,
    @Json(name = "dividendYield") val dividendYield: Double? = null,
    @Json(name = "fiftyTwoWeekHigh") val fiftyTwoWeekHigh: Double? = null,
    @Json(name = "fiftyTwoWeekLow") val fiftyTwoWeekLow: Double? = null
)
