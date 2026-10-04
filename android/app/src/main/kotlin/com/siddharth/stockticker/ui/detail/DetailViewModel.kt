package com.siddharth.stockticker.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siddharth.stockticker.data.local.StockEntity
import com.siddharth.stockticker.data.model.ChartPeriod
import com.siddharth.stockticker.data.model.PricePoint
import com.siddharth.stockticker.data.repository.StockRepository
import com.siddharth.stockticker.util.Resource
import com.siddharth.stockticker.util.SeriesStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the Returns page. */
data class PeriodReturn(val label: String, val value: Double?)

/** Chart state for the currently shown symbol. */
data class ChartState(
    val period: ChartPeriod = ChartPeriod.default,
    val points: List<PricePoint> = emptyList(),
    /**
     * Null until the user scrubs: the chart opens clean, with no marker or
     * tooltip sitting on the line, and only grows one once a finger asks.
     */
    val selectedIndex: Int? = null,
    val isLoading: Boolean = false
)

data class DetailUiState(
    val symbol: String,
    val stock: StockEntity? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null
) {
    val hasData: Boolean get() = stock?.price != null

    /** Where the price sits in its 52-week range, for the hero ring. */
    val rangePosition: Float?
        get() = SeriesStats.rangePosition(stock?.price, stock?.week52Low, stock?.week52High)

    /**
     * The Returns page rows. 1D comes from the live quote rather than the
     * history series, so it always agrees with the day change on the chart page.
     */
    val returns: List<PeriodReturn>
        get() = listOf(
            PeriodReturn("1D", stock?.changePercent),
            PeriodReturn("1W", stock?.return1w),
            PeriodReturn("1M", stock?.return1m),
            PeriodReturn("3M", stock?.return3m),
            PeriodReturn("6M", stock?.return6m),
            PeriodReturn("1Y", stock?.return1y)
        )

    /** Scale for the returns bars: the biggest magnitude on show. */
    val returnsScale: Double
        get() = returns.mapNotNull { it.value }
            .maxOfOrNull { kotlin.math.abs(it) } ?: 1.0
}

/**
 * Drives the detail screen for one symbol.
 *
 * The horizontal pager on this screen moves between *content pages* (Chart,
 * Fundamentals, Returns, Risk), so moving between watchlist symbols is a
 * separate action exposed at the end of the Risk page.
 */
class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StockRepository.get(application)

    private val _symbol = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _chart = MutableStateFlow(ChartState())

    val symbol: StateFlow<String?> = _symbol.asStateFlow()
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val chart: StateFlow<ChartState> = _chart.asStateFlow()

    /** Watchlist symbols in order, for the prev/next action. */
    val symbols: StateFlow<List<String>> = repository.watchlist
        .map { items -> items.map { it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var refreshJob: Job? = null
    private var chartJob: Job? = null

    fun select(symbol: String) {
        if (_symbol.value == symbol) return
        _symbol.value = symbol
        _error.value = null
        // A new symbol keeps the chosen period but must drop the old series,
        // or the chart would briefly show one symbol's prices under another's
        // name.
        _chart.value = ChartState(period = _chart.value.period, isLoading = true)
        load(symbol, force = false)
        loadPoints(symbol, _chart.value.period)
    }

    fun observe(symbol: String) = repository.observeStock(symbol)

    fun setPeriod(period: ChartPeriod) {
        if (_chart.value.period == period) return
        _chart.value = _chart.value.copy(period = period, isLoading = true)
        _symbol.value?.let { loadPoints(it, period) }
    }

    /** Advances the chart period by [delta] steps; used by crown/bezel rotation. */
    fun nudgePeriod(delta: Int) {
        val all = ChartPeriod.entries
        val next = (_chart.value.period.ordinal + delta).coerceIn(0, all.lastIndex)
        setPeriod(all[next])
    }

    fun selectPoint(index: Int) {
        val points = _chart.value.points
        if (points.isEmpty()) return
        _chart.value = _chart.value.copy(
            selectedIndex = index.coerceIn(0, points.lastIndex)
        )
    }

    fun retry() {
        _symbol.value?.let {
            load(it, force = true)
            loadPoints(it, _chart.value.period)
        }
    }

    private fun loadPoints(symbol: String, period: ChartPeriod) {
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _chart.value = _chart.value.copy(isLoading = true)
            val points = repository.points(symbol, period)
            _chart.value = ChartState(
                period = period,
                points = points,
                // Switching period clears the marker rather than guessing where
                // the user wants it on a series they have not seen yet.
                selectedIndex = null,
                isLoading = false
            )
        }
    }

    private fun load(symbol: String, force: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = repository.refreshDetail(symbol, force = force)) {
                is Resource.Error -> _error.value = result.message
                is Resource.Success -> _error.value = null
                Resource.Loading -> Unit
            }
            _isRefreshing.value = false
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        chartJob?.cancel()
        super.onCleared()
    }
}
