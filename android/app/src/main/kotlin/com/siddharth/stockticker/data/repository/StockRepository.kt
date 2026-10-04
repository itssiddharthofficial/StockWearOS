package com.siddharth.stockticker.data.repository

import android.content.Context
import android.util.Log
import com.siddharth.stockticker.data.local.StockDao
import com.siddharth.stockticker.data.model.ChartPeriod
import com.siddharth.stockticker.data.model.PricePoint
import com.siddharth.stockticker.data.local.StockDatabase
import com.siddharth.stockticker.data.local.StockEntity
import com.siddharth.stockticker.data.local.WatchlistEntity
import com.siddharth.stockticker.data.remote.MarketDataSource
import com.siddharth.stockticker.data.remote.dto.QuoteDto
import com.siddharth.stockticker.util.Resource
import com.siddharth.stockticker.util.SeriesStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single entry point to market data. Room is the source of truth: the UI always
 * observes the cache, so a cold start paints instantly and a failed network call
 * can never blank the screen. Every exception is caught here and converted to a
 * [Resource.Error] — nothing above this class handles IO failures.
 */
class StockRepository private constructor(
    private val dao: StockDao,
    private val remote: MarketDataSource
) {

    private val pointsCache = LinkedHashMap<String, Pair<Long, List<PricePoint>>>()
    private val pointsMutex = Mutex()
    private val benchmarkMutex = Mutex()

    @Volatile private var benchmarkCache: Pair<Long, SeriesStats.Series>? = null

    /** A watchlist slot joined with whatever quote we have cached for it. */
    data class WatchlistItem(
        val symbol: String,
        val position: Int,
        val name: String,
        val stock: StockEntity?
    ) {
        val changePercent: Double? get() = stock?.changePercent
        val price: Double? get() = stock?.price
        val trend: List<Double> get() = stock?.trendPoints.orEmpty()
        val isDegraded: Boolean
            get() = stock == null || stock.price == null
    }

    // ---- Observation (always from cache) --------------------------------

    val watchlist: Flow<List<WatchlistItem>> =
        dao.observeWatchlist().combine(dao.observeAllStocks()) { entries, stocks ->
            val bySymbol = stocks.associateBy { it.symbol }
            entries.map { entry ->
                val cached = bySymbol[entry.symbol]
                WatchlistItem(
                    symbol = entry.symbol,
                    position = entry.position,
                    name = cached?.name
                        ?: SymbolCatalog.nameFor(entry.symbol)
                        ?: entry.symbol,
                    stock = cached
                )
            }
        }

    fun observeStock(symbol: String): Flow<StockEntity?> = dao.observeStock(symbol)

    suspend fun watchlistSymbols(): List<String> = dao.watchlistSymbols()

    suspend fun watchlistCount(): Int = dao.watchlistCount()

    // ---- Watchlist management -------------------------------------------

    /** Seeds the five defaults on first launch only. */
    suspend fun seedDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.watchlistCount() > 0) return@withContext
        SymbolCatalog.defaultWatchlist.forEachIndexed { index, symbol ->
            dao.insertWatchlistEntry(WatchlistEntity(symbol, index))
        }
    }

    sealed interface AddResult {
        data object Added : AddResult
        data object AlreadyPresent : AddResult
        data object Full : AddResult
    }

    suspend fun addToWatchlist(symbol: String): AddResult = withContext(Dispatchers.IO) {
        if (dao.isInWatchlist(symbol) > 0) return@withContext AddResult.AlreadyPresent
        val count = dao.watchlistCount()
        if (count >= MAX_WATCHLIST) return@withContext AddResult.Full
        dao.insertWatchlistEntry(WatchlistEntity(symbol, count))
        // Seed a row so the new bubble has a name immediately, before any fetch.
        if (dao.getStock(symbol) == null) {
            dao.upsertStock(
                StockEntity(symbol = symbol, name = SymbolCatalog.nameFor(symbol))
            )
        }
        AddResult.Added
    }

    suspend fun removeFromWatchlist(symbol: String) = withContext(Dispatchers.IO) {
        dao.deleteWatchlistEntry(symbol)
        dao.compactPositions()
        dao.pruneOrphanQuotes()
    }

    // ---- Refresh --------------------------------------------------------

    /**
     * Refreshes every watchlist symbol. Batched into one quote call; symbols the
     * batch didn't cover fall back to the chart endpoint individually, so one bad
     * symbol degrades to a single greyed bubble rather than an app-wide error.
     */
    suspend fun refreshWatchlist(force: Boolean = false): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val symbols = dao.watchlistSymbols()
            if (symbols.isEmpty()) return@withContext Resource.Success(Unit)

            if (!force && symbols.all { isQuoteFresh(it) }) {
                return@withContext Resource.Success(Unit)
            }

            runCatching {
                val quotes = remote.quotes(symbols)
                val bySymbol = quotes.associateBy { it.symbol!!.uppercase() }
                val now = System.currentTimeMillis()

                val updated = mutableListOf<StockEntity>()
                val missing = mutableListOf<String>()

                symbols.forEach { symbol ->
                    val dto = bySymbol[symbol.uppercase()]
                    if (dto != null) {
                        updated += mergeQuote(dao.getStock(symbol), symbol, dto, now)
                    } else {
                        missing += symbol
                    }
                }
                if (updated.isNotEmpty()) dao.upsertStocks(updated)

                // Per-symbol fallback for whatever the batch endpoint skipped.
                val recovered = coroutineScope {
                    missing.map { symbol ->
                        async { symbol to remote.chart(symbol, "5d", "1d") }
                    }.awaitAll()
                }
                var failures = 0
                recovered.forEach { (symbol, result) ->
                    val existing = dao.getStock(symbol)
                    if (result?.meta?.regularMarketPrice != null) {
                        dao.upsertStock(mergeChartMeta(existing, symbol, result, now))
                    } else {
                        failures++
                        dao.upsertStock(
                            (existing ?: StockEntity(symbol, SymbolCatalog.nameFor(symbol)))
                                .copy(lastErrorAt = now)
                        )
                    }
                }

                refreshTrendsIfStale(symbols, now)

                when {
                    updated.isEmpty() && failures == symbols.size ->
                        Resource.Error(ERROR_UNREACHABLE)
                    failures > 0 -> Resource.Success(Unit, isStale = true)
                    else -> Resource.Success(Unit)
                }
            }.getOrElse { throwable ->
                // A superseded refresh is not a failure. Swallowing the
                // cancellation would both break structured concurrency and
                // flash a bogus "can't reach the service" state on screen.
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "refreshWatchlist failed", throwable)
                Resource.Error(ERROR_UNREACHABLE, throwable)
            }
        }

    /**
     * Refreshes one symbol's quote plus the derived history stats (period
     * returns, volatility, beta). The history call is the expensive one, so it is
     * skipped when the cached stats are less than a day old.
     */
    suspend fun refreshDetail(symbol: String, force: Boolean = false): Resource<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val existing = dao.getStock(symbol)

                var quoteOk = existing?.hasQuote == true
                if (force || !isQuoteFresh(symbol)) {
                    val dto = remote.quotes(listOf(symbol)).firstOrNull()
                    if (dto != null) {
                        dao.upsertStock(mergeQuote(dao.getStock(symbol), symbol, dto, now))
                        quoteOk = true
                    }
                }

                val needsHistory = force ||
                    existing == null ||
                    now - existing.detailUpdatedAt > HISTORY_TTL_MS
                if (needsHistory) {
                    val stats = computeHistoryStats(symbol)
                    if (stats != null) {
                        val base = dao.getStock(symbol)
                            ?: StockEntity(symbol, SymbolCatalog.nameFor(symbol))
                        dao.upsertStock(
                            base.copy(
                                price = base.price ?: stats.latestClose,
                                week52High = base.week52High ?: stats.week52High,
                                week52Low = base.week52Low ?: stats.week52Low,
                                return1w = stats.return1w,
                                return1m = stats.return1m,
                                return3m = stats.return3m,
                                return6m = stats.return6m,
                                return1y = stats.return1y,
                                volatility = stats.volatility,
                                beta = stats.beta,
                                detailUpdatedAt = now
                            )
                        )
                        quoteOk = quoteOk || stats.latestClose != null
                    }
                }

                if (quoteOk) Resource.Success(Unit) else Resource.Error(ERROR_UNREACHABLE)
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "refreshDetail($symbol) failed", throwable)
                Resource.Error(ERROR_UNREACHABLE, throwable)
            }
        }

    /** Symbol search: local catalogue first (instant, offline), remote merged in. */
    suspend fun search(query: String): List<SymbolCatalog.Entry> =
        withContext(Dispatchers.IO) {
            val local = SymbolCatalog.filter(query)
            if (query.trim().length < 2) return@withContext local

            val remoteHits = remote.search(query.trim())
                .mapNotNull { SymbolCatalog.fromRemote(it) }

            val seen = linkedMapOf<String, SymbolCatalog.Entry>()
            local.forEach { seen[it.symbol] = it }
            remoteHits.forEach { seen.putIfAbsent(it.symbol, it) }
            seen.values.toList().take(30)
        }

    // ---- Internals ------------------------------------------------------

    private fun pointsTtlMs(period: ChartPeriod): Long =
        if (period.isIntraday) POINTS_TTL_INTRADAY_MS else POINTS_TTL_MS

    private suspend fun isQuoteFresh(symbol: String): Boolean {
        val cached = dao.getStock(symbol) ?: return false
        if (!cached.hasQuote) return false
        return System.currentTimeMillis() - cached.quoteUpdatedAt < QUOTE_TTL_MS
    }

    private data class HistoryStats(
        val latestClose: Double?,
        val week52High: Double?,
        val week52Low: Double?,
        val return1w: Double?,
        val return1m: Double?,
        val return3m: Double?,
        val return6m: Double?,
        val return1y: Double?,
        val volatility: Double?,
        val beta: Double?
    )

    /**
     * One year of daily closes covers every return window the Returns page
     * shows, and sampling it weekly gives the risk stats their inputs too, so
     * the whole detail screen costs one history call per symbol rather than one
     * per window. The Nifty benchmark is fetched once and shared across symbols.
     */
    private suspend fun computeHistoryStats(symbol: String): HistoryStats? = coroutineScope {
        val stockDeferred = async { remote.chart(symbol, "1y", "1d") }
        val benchmarkDeferred = async {
            if (symbol == SymbolCatalog.NIFTY) null else benchmarkSeries()
        }
        val stockResult = stockDeferred.await() ?: return@coroutineScope null
        val closes = stockResult.indicators?.quote?.firstOrNull()?.close
            ?: stockResult.indicators?.adjclose?.firstOrNull()?.adjclose
        val daily = SeriesStats.clean(stockResult.timestamp, closes)
        if (!daily.isUsable) {
            benchmarkDeferred.await()
            return@coroutineScope null
        }

        // Risk stats stay on weekly samples, which is what the disclosure line
        // on the Risk page tells the user they are.
        val weekly = SeriesStats.sampleWeekly(daily)
        val benchmarkWeekly = benchmarkDeferred.await()

        HistoryStats(
            latestClose = daily.closes.lastOrNull(),
            week52High = stockResult.meta?.fiftyTwoWeekHigh ?: daily.closes.maxOrNull(),
            week52Low = stockResult.meta?.fiftyTwoWeekLow ?: daily.closes.minOrNull(),
            return1w = SeriesStats.periodReturn(daily, 7),
            return1m = SeriesStats.periodReturn(daily, 30),
            return3m = SeriesStats.periodReturn(daily, 91),
            return6m = SeriesStats.periodReturn(daily, 182),
            return1y = SeriesStats.periodReturn(daily, 365),
            volatility = SeriesStats.annualisedVolatility(weekly),
            beta = benchmarkWeekly?.let { SeriesStats.beta(weekly, it) }
        )
    }

    /**
     * Tops up the home sparkline series for any symbol whose cached trend is
     * missing or old.
     *
     * Deliberately not part of the every-open refresh path: a month of daily
     * closes barely moves within a day, so refreshing it on the quote cadence
     * would turn a one-call refresh into a six-call one for a line 44dp wide.
     */
    private suspend fun refreshTrendsIfStale(symbols: List<String>, now: Long) {
        val stale = symbols.filter { symbol ->
            val cached = dao.getStock(symbol)
            cached?.trend.isNullOrBlank() || now - (cached?.trendUpdatedAt ?: 0L) > TREND_TTL_MS
        }
        if (stale.isEmpty()) return

        coroutineScope {
            stale.map { symbol ->
                async { symbol to remote.chart(symbol, "1mo", "1d") }
            }.awaitAll()
        }.forEach { (symbol, result) ->
            val closes = result?.indicators?.quote?.firstOrNull()?.close ?: return@forEach
            val series = SeriesStats.clean(result.timestamp, closes)
            if (!series.isUsable) return@forEach
            val points = thinTo(series.closes, TREND_POINTS)
            val base = dao.getStock(symbol) ?: return@forEach
            dao.upsertStock(
                base.copy(
                    trend = points.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) },
                    trendUpdatedAt = now
                )
            )
        }
    }

    /** Evenly spaced sample, always keeping the first and last points. */
    private fun thinTo(values: List<Double>, target: Int): List<Double> {
        if (values.size <= target || target < 2) return values
        val out = ArrayList<Double>(target)
        val step = (values.size - 1).toDouble() / (target - 1)
        for (i in 0 until target) {
            out += values[kotlin.math.round(i * step).toInt().coerceIn(values.indices)]
        }
        return out
    }

    /** Nifty weekly closes, cached in memory so five symbols share one fetch. */
    private suspend fun benchmarkSeries(): SeriesStats.Series? = benchmarkMutex.withLock {
        val cached = benchmarkCache
        if (cached != null && System.currentTimeMillis() - cached.first < BENCHMARK_TTL_MS) {
            return@withLock cached.second
        }
        val result = remote.chart(SymbolCatalog.NIFTY, "1y", "1d")
        val series = result?.let {
            SeriesStats.sampleWeekly(
                SeriesStats.clean(it.timestamp, it.indicators?.quote?.firstOrNull()?.close)
            )
        }?.takeIf { it.isUsable }
        if (series != null) benchmarkCache = System.currentTimeMillis() to series
        series
    }

    // ---- Chart price series ---------------------------------------------

    /**
     * Close prices for one period tab.
     *
     * Held in memory rather than Room: a price series is per-period view state,
     * not the source of truth for the watchlist, and persisting six series per
     * symbol would cost more than re-fetching one on demand. Offline with
     * nothing cached, the chart shows its unavailable state and the rest of the
     * screen still renders from the Room cache.
     */
    suspend fun points(symbol: String, period: ChartPeriod): List<PricePoint> =
        withContext(Dispatchers.IO) {
            val key = symbol + "|" + period.name
            pointsMutex.withLock {
                pointsCache[key]?.takeIf {
                    System.currentTimeMillis() - it.first < pointsTtlMs(period)
                }?.second
            }?.let { return@withContext it }

            val fetched = runCatching { remote.points(symbol, period) }
                .getOrElse { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "points failed for " + symbol, throwable)
                    emptyList()
                }
            if (fetched.isNotEmpty()) {
                pointsMutex.withLock {
                    pointsCache[key] = System.currentTimeMillis() to fetched
                    // Small bound: five watchlist symbols times six periods.
                    if (pointsCache.size > POINTS_CACHE_LIMIT) {
                        pointsCache.entries
                            .sortedBy { it.value.first }
                            .take(pointsCache.size - POINTS_CACHE_LIMIT)
                            .map { it.key }
                            .forEach { pointsCache.remove(it) }
                    }
                }
            }
            fetched
        }

    private fun mergeQuote(
        existing: StockEntity?,
        symbol: String,
        dto: QuoteDto,
        now: Long
    ): StockEntity {
        val base = existing ?: StockEntity(symbol = symbol)
        val price = dto.regularMarketPrice
        val previous = dto.regularMarketPreviousClose
        val change = dto.regularMarketChange
            ?: if (price != null && previous != null) price - previous else null
        val changePercent = dto.regularMarketChangePercent
            ?: if (change != null && previous != null && previous != 0.0) {
                change / previous * 100.0
            } else {
                null
            }
        // Yahoo reports this either as a fraction (0.023) or a percent (2.3)
        // depending on the field; normalise to percent and drop absurd values.
        val yieldPercent = dto.dividendYield?.takeIf { it in 0.0..100.0 }
            ?: dto.trailingAnnualDividendYield?.takeIf { it > 0.0 }?.let { it * 100.0 }

        return base.copy(
            symbol = symbol,
            name = dto.longName ?: dto.shortName ?: dto.displayName
                ?: base.name ?: SymbolCatalog.nameFor(symbol),
            price = price ?: base.price,
            previousClose = previous ?: base.previousClose,
            change = change ?: base.change,
            changePercent = changePercent ?: base.changePercent,
            currency = dto.currency ?: base.currency,
            exchange = dto.fullExchangeName ?: base.exchange,
            marketCap = dto.marketCap?.takeIf { it > 0 } ?: base.marketCap,
            peRatio = dto.trailingPE?.takeIf { it > 0 } ?: base.peRatio,
            dividendYield = yieldPercent ?: base.dividendYield,
            week52High = dto.fiftyTwoWeekHigh ?: base.week52High,
            week52Low = dto.fiftyTwoWeekLow ?: base.week52Low,
            dayHigh = dto.regularMarketDayHigh ?: base.dayHigh,
            dayLow = dto.regularMarketDayLow ?: base.dayLow,
            volume = dto.regularMarketVolume ?: base.volume,
            quoteUpdatedAt = if (price != null) now else base.quoteUpdatedAt,
            lastErrorAt = if (price != null) 0L else base.lastErrorAt
        )
    }

    private fun mergeChartMeta(
        existing: StockEntity?,
        symbol: String,
        result: com.siddharth.stockticker.data.remote.dto.ChartResult,
        now: Long
    ): StockEntity {
        val base = existing ?: StockEntity(symbol = symbol)
        val meta = result.meta
        val price = meta?.regularMarketPrice
        val previous = meta?.previousClose ?: meta?.chartPreviousClose
        val change = if (price != null && previous != null) price - previous else null
        val changePercent = if (change != null && previous != null && previous != 0.0) {
            change / previous * 100.0
        } else {
            null
        }
        return base.copy(
            symbol = symbol,
            name = meta?.longName ?: meta?.shortName ?: base.name
                ?: SymbolCatalog.nameFor(symbol),
            price = price ?: base.price,
            previousClose = previous ?: base.previousClose,
            change = change ?: base.change,
            changePercent = changePercent ?: base.changePercent,
            currency = meta?.currency ?: base.currency,
            exchange = meta?.fullExchangeName ?: meta?.exchangeName ?: base.exchange,
            week52High = meta?.fiftyTwoWeekHigh ?: base.week52High,
            week52Low = meta?.fiftyTwoWeekLow ?: base.week52Low,
            dayHigh = meta?.regularMarketDayHigh ?: base.dayHigh,
            dayLow = meta?.regularMarketDayLow ?: base.dayLow,
            volume = meta?.regularMarketVolume ?: base.volume,
            quoteUpdatedAt = if (price != null) now else base.quoteUpdatedAt,
            lastErrorAt = if (price != null) 0L else now
        )
    }

    companion object {
        const val MAX_WATCHLIST = 5
        const val ERROR_UNREACHABLE = "Can't reach the market data service"

        /** Matches the UI's refresh-on-open cadence; no point fetching fresher. */
        private const val QUOTE_TTL_MS = 60_000L

        /** History barely moves intraday; a day is plenty. */
        private const val HISTORY_TTL_MS = 24 * 60 * 60 * 1000L

        private const val TAG = "StockRepository"

        /** Intraday series move; the longer windows do not within a session. */
        private const val POINTS_TTL_INTRADAY_MS = 60_000L
        private const val POINTS_TTL_MS = 15 * 60_000L
        private const val POINTS_CACHE_LIMIT = 36
        private const val BENCHMARK_TTL_MS = 60 * 60_000L

        /** A month of daily closes barely moves within a few hours. */
        private const val TREND_TTL_MS = 6 * 60 * 60_000L
        private const val TREND_POINTS = 20

        @Volatile private var instance: StockRepository? = null

        fun get(context: Context): StockRepository =
            instance ?: synchronized(this) {
                instance ?: StockRepository(
                    dao = StockDatabase.get(context).stockDao(),
                    remote = MarketDataSource()
                ).also { instance = it }
            }
    }
}

/** Local `awaitAll` so the repository doesn't pull in another import surface. */
private suspend fun <T> List<kotlinx.coroutines.Deferred<T>>.awaitAll(): List<T> =
    kotlinx.coroutines.awaitAll(*this.toTypedArray())
