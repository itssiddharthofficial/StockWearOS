package com.siddharth.stockticker.data.remote

import com.siddharth.stockticker.data.remote.dto.ChartResponse
import com.siddharth.stockticker.data.remote.dto.QuoteResponse
import com.siddharth.stockticker.data.remote.dto.SearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The market-data contract the app codes against. The default implementation
 * talks to Yahoo's public web endpoints directly (no backend to host, so the
 * APK works standalone on the watch); [FlaskApiService] in the same package
 * swaps in the optional self-hosted proxy without touching the UI layer.
 */
interface StockApiService {

    /** Batched quotes + fundamentals. Needs the cookie/crumb handshake. */
    @GET("v7/finance/quote")
    suspend fun quotes(
        @Query("symbols") symbols: String,
        @Query("crumb") crumb: String?
    ): Response<QuoteResponse>

    /** Price history. Works with no handshake, so it is also the price fallback. */
    @GET("v8/finance/chart/{symbol}")
    suspend fun chart(
        @Path("symbol") symbol: String,
        @Query("range") range: String,
        @Query("interval") interval: String
    ): Response<ChartResponse>

    @GET("v1/finance/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 20,
        @Query("newsCount") newsCount: Int = 0,
        @Query("enableFuzzyQuery") fuzzy: Boolean = false
    ): Response<SearchResponse>

    /** Session crumb, returned as a bare token in the body. */
    @GET("v1/test/getcrumb")
    suspend fun crumb(): Response<String>
}
