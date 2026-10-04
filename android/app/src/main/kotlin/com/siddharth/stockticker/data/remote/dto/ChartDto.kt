package com.siddharth.stockticker.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * `/v8/finance/chart/{symbol}` — the one upstream endpoint that needs no
 * cookie/crumb handshake, so it doubles as the fallback price source.
 * Every field is nullable on purpose; the shape is undocumented.
 */
@JsonClass(generateAdapter = true)
data class ChartResponse(
    val chart: ChartBody? = null
)

@JsonClass(generateAdapter = true)
data class ChartBody(
    val result: List<ChartResult>? = null,
    val error: ChartError? = null
)

@JsonClass(generateAdapter = true)
data class ChartError(
    val code: String? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class ChartResult(
    val meta: ChartMeta? = null,
    val timestamp: List<Long?>? = null,
    val indicators: ChartIndicators? = null
)

@JsonClass(generateAdapter = true)
data class ChartMeta(
    val currency: String? = null,
    val symbol: String? = null,
    @Json(name = "exchangeName") val exchangeName: String? = null,
    @Json(name = "fullExchangeName") val fullExchangeName: String? = null,
    @Json(name = "longName") val longName: String? = null,
    @Json(name = "shortName") val shortName: String? = null,
    @Json(name = "regularMarketPrice") val regularMarketPrice: Double? = null,
    @Json(name = "previousClose") val previousClose: Double? = null,
    @Json(name = "chartPreviousClose") val chartPreviousClose: Double? = null,
    @Json(name = "regularMarketDayHigh") val regularMarketDayHigh: Double? = null,
    @Json(name = "regularMarketDayLow") val regularMarketDayLow: Double? = null,
    @Json(name = "regularMarketVolume") val regularMarketVolume: Long? = null,
    @Json(name = "fiftyTwoWeekHigh") val fiftyTwoWeekHigh: Double? = null,
    @Json(name = "fiftyTwoWeekLow") val fiftyTwoWeekLow: Double? = null
)

@JsonClass(generateAdapter = true)
data class ChartIndicators(
    val quote: List<ChartQuote>? = null,
    val adjclose: List<ChartAdjClose>? = null
)

@JsonClass(generateAdapter = true)
data class ChartQuote(
    val open: List<Double?>? = null,
    val close: List<Double?>? = null,
    val high: List<Double?>? = null,
    val low: List<Double?>? = null,
    val volume: List<Long?>? = null
)

@JsonClass(generateAdapter = true)
data class ChartAdjClose(
    val adjclose: List<Double?>? = null
)
