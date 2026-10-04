package com.siddharth.stockticker.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** `/v1/finance/search?q=` — symbol lookup. No auth handshake needed. */
@JsonClass(generateAdapter = true)
data class SearchResponse(
    val quotes: List<SearchQuoteDto>? = null
)

@JsonClass(generateAdapter = true)
data class SearchQuoteDto(
    val symbol: String? = null,
    @Json(name = "shortname") val shortName: String? = null,
    @Json(name = "longname") val longName: String? = null,
    @Json(name = "exchDisp") val exchangeDisplay: String? = null,
    @Json(name = "typeDisp") val typeDisplay: String? = null,
    @Json(name = "quoteType") val quoteType: String? = null
)
