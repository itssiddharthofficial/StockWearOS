package com.siddharth.stockticker.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siddharth.stockticker.data.repository.StockRepository
import com.siddharth.stockticker.util.ConnectivityObserver
import com.siddharth.stockticker.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home state. The list always comes from Room, so `items` is populated before
 * any network call returns; `isRefreshing` and `error` describe the *fetch*, and
 * never gate the content.
 */
data class HomeUiState(
    val items: List<StockRepository.WatchlistItem> = emptyList(),
    val isLoadingFirstTime: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val error: String? = null,
    val isStale: Boolean = false
) {
    /** Equal-weighted average day change across the slots that have data. */
    val aggregateChangePercent: Double?
        get() {
            val values = items.mapNotNull { it.changePercent }
            return if (values.isEmpty()) null else values.average()
        }

    val lastUpdatedAt: Long?
        get() = items.mapNotNull { it.stock?.quoteUpdatedAt?.takeIf { ts -> ts > 0L } }.maxOrNull()

    val isEmpty: Boolean get() = items.isEmpty() && !isLoadingFirstTime
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StockRepository.get(application)
    private val connectivity = ConnectivityObserver(application)

    private val fetchState = MutableStateFlow(FetchState())
    private var refreshJob: Job? = null

    private data class FetchState(
        val isLoadingFirstTime: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val isStale: Boolean = false
    )

    val uiState: StateFlow<HomeUiState> = combine(
        repository.watchlist,
        fetchState,
        connectivity.isOnline
    ) { items, fetch, online ->
        HomeUiState(
            items = items,
            // "First time" means there is genuinely nothing to paint yet.
            isLoadingFirstTime = fetch.isLoadingFirstTime && items.none { it.stock?.price != null },
            isRefreshing = fetch.isRefreshing,
            isOnline = online,
            error = fetch.error,
            isStale = fetch.isStale
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    private val _removalTarget = MutableStateFlow<String?>(null)
    val removalTarget: StateFlow<String?> = _removalTarget.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
            refresh(force = false)
        }
    }

    /**
     * Refresh-on-open. The 15-minute WorkManager floor is a platform limit, so
     * freshness *when looked at* comes from here, not from the background worker.
     */
    fun refresh(force: Boolean = true) {
        // Supersede an in-flight refresh instead of stacking radio work.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            fetchState.update { it.copy(isRefreshing = true, error = null) }
            when (val result = repository.refreshWatchlist(force = force)) {
                is Resource.Success -> fetchState.update {
                    it.copy(
                        isLoadingFirstTime = false,
                        isRefreshing = false,
                        error = null,
                        isStale = result.isStale
                    )
                }
                is Resource.Error -> fetchState.update {
                    it.copy(
                        isLoadingFirstTime = false,
                        isRefreshing = false,
                        error = result.message,
                        isStale = true
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askToRemove(symbol: String) { _removalTarget.value = symbol }

    fun cancelRemoval() { _removalTarget.value = null }

    fun confirmRemoval() {
        val symbol = _removalTarget.value ?: return
        _removalTarget.value = null
        viewModelScope.launch { repository.removeFromWatchlist(symbol) }
    }

    /** Clears a transient error banner without re-fetching. */
    fun dismissError() = fetchState.update { it.copy(error = null) }

    override fun onCleared() {
        // Nothing in flight should outlive the screen; a response nobody will
        // see is wasted radio time on a watch.
        refreshJob?.cancel()
        super.onCleared()
    }
}
