package com.siddharth.stockticker.data.remote

import android.util.Log
import com.siddharth.stockticker.data.model.ChartPeriod
import com.siddharth.stockticker.data.model.PricePoint
import com.siddharth.stockticker.data.model.lastSessionOnly
import com.siddharth.stockticker.data.model.thinPoints
import com.siddharth.stockticker.data.remote.dto.ChartResult
import com.siddharth.stockticker.data.remote.dto.QuoteDto
import com.siddharth.stockticker.data.remote.dto.SearchQuoteDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Thin wrapper over [StockApiService] that owns the quirks of the upstream feed:
 * the cookie/crumb handshake, the "quote endpoint failed, fall back to chart"
 * path, and turning HTTP/parse failures into `null` rather than exceptions.
 *
 * Nothing above this class ever sees an [java.io.IOException].
 */
class MarketDataSource(
    private val service: StockApiService = ApiClient.service
) {

    private val crumbMutex = Mutex()
    @Volatile private var crumb: String? = null

    /**
     * Gets (and caches) a session crumb. Returns null if the handshake fails —
     * callers must stay functional without it.
     */
    private suspend fun ensureCrumb(force: Boolean = false): String? = crumbMutex.withLock {
        if (!force) crumb?.let { return@withLock it }
        crumb = null
        runCatching {
            // This request is expected to 404; it exists to seed the session cookie.
            withContext(Dispatchers.IO) {
                ApiClient.okHttp.newCall(
                    Request.Builder().url(ApiClient.COOKIE_URL).build()
                ).execute().use { it.code }
            }
            val response = service.crumb()
            val token = response.body()?.trim()
            if (response.isSuccessful && !token.isNullOrBlank() && token.length < 64) {
                token
            } else {
                null
            }
        }.onFailure { rethrowIfCancelled(it); Log.w(TAG, "Crumb handshake failed: ${it.message}") }
            .getOrNull()
            .also { crumb = it }
    }

    /**
     * Batched quotes for up to the whole watchlist in one round trip.
     * Returns an empty list (never throws) when the endpoint is unavailable.
     */
    suspend fun quotes(symbols: List<String>): List<QuoteDto> {
        if (symbols.isEmpty()) return emptyList()
        val joined = symbols.joinToString(",")

        suspend fun attempt(token: String?): List<QuoteDto>? = runCatching {
            val response = service.quotes(joined, token)
            if (!response.isSuccessful) return@runCatching null
            response.body()?.quoteResponse?.result?.filter { it.symbol != null }
        }.onFailure { rethrowIfCancelled(it); Log.w(TAG, "quotes() failed: ${it.message}") }.getOrNull()

        attempt(ensureCrumb())?.takeIf { it.isNotEmpty() }?.let { return it }
        // A stale crumb looks exactly like an auth failure; retry once with a fresh one.
        attempt(ensureCrumb(force = true))?.takeIf { it.isNotEmpty() }?.let { return it }
        return emptyList()
    }

    /**
     * Price history. [range]/[interval] use Yahoo's vocabulary
     * (`10y`/`1wk`, `1d`/`5m`, ...). Null on any failure.
     */
    suspend fun chart(symbol: String, range: String, interval: String): ChartResult? =
        runCatching {
            val response = service.chart(symbol, range, interval)
            if (!response.isSuccessful) return@runCatching null
            response.body()?.chart?.result?.firstOrNull()
        }.onFailure { rethrowIfCancelled(it); Log.w(TAG, "chart($symbol) failed: ${it.message}") }.getOrNull()

    /**
     * Close prices for one period tab, trimmed to the chart's point budget.
     *
     * Returns an empty list rather than throwing: the chart has an explicit
     * "unavailable" state and must never take the screen down with it.
     */
    suspend fun points(symbol: String, period: ChartPeriod): List<PricePoint> {
        val result = chart(symbol, period.range, period.interval) ?: return emptyList()
        val quote = result.indicators?.quote?.firstOrNull() ?: return emptyList()
        val stamps = result.timestamp ?: return emptyList()
        val closes = quote.close ?: result.indicators.adjclose?.firstOrNull()?.adjclose
            ?: return emptyList()

        val raw = ArrayList<PricePoint>(stamps.size)
        for (i in stamps.indices) {
            val ts = stamps[i] ?: continue
            val close = closes.getOrNull(i) ?: continue
            // A null close is a gap in the feed, not a flat price. Dropping it
            // keeps the line honest; carrying the previous value forward would
            // draw a plateau that never traded.
            if (close <= 0.0 || close.isNaN()) continue
            raw += PricePoint(ts, close)
        }
        // The intraday tabs ask upstream for several sessions so they still
        // have data on a weekend; only the most recent one belongs on a 1D
        // chart. The longer intraday windows keep every session they fetched.
        val scoped = if (period == ChartPeriod.ONE_DAY) lastSessionOnly(raw) else raw
        return thinPoints(scoped)
    }

    /** Symbol search. Returns an empty list when offline or rate-limited. */
    suspend fun search(query: String): List<SearchQuoteDto> = runCatching {
        val response = service.search(query)
        if (!response.isSuccessful) return@runCatching emptyList()
        response.body()?.quotes
            ?.filter { !it.symbol.isNullOrBlank() }
            ?.filter { it.quoteType.equals("EQUITY", true) || it.quoteType.equals("INDEX", true) }
            .orEmpty()
    }.onFailure { rethrowIfCancelled(it); Log.w(TAG, "search() failed: ${it.message}") }.getOrNull().orEmpty()

    /**
     * Cancellation is not a network failure: it means the caller moved on. It
     * must propagate, or structured concurrency silently stops working and a
     * superseded request reports itself as an outage.
     */
    private fun rethrowIfCancelled(throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
    }

    private companion object {
        const val TAG = "MarketDataSource"
    }
}
