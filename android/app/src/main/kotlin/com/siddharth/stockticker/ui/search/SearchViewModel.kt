package com.siddharth.stockticker.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siddharth.stockticker.data.repository.StockRepository
import com.siddharth.stockticker.data.repository.SymbolCatalog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SymbolCatalog.Entry> = SymbolCatalog.entries.take(24),
    val isSearching: Boolean = false,
    val slotsUsed: Int = 0,
    val notice: String? = null,
    val justAdded: String? = null
) {
    val isFull: Boolean get() = slotsUsed >= StockRepository.MAX_WATCHLIST
    val slotLabel: String get() = "$slotsUsed/${StockRepository.MAX_WATCHLIST}"
}

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StockRepository.get(application)

    private val _query = MutableStateFlow("")
    private val _results = MutableStateFlow(SymbolCatalog.entries.take(24))
    private val _isSearching = MutableStateFlow(false)
    private val _notice = MutableStateFlow<String?>(null)
    private val _justAdded = MutableStateFlow<String?>(null)

    /** Symbols already on the watchlist, so results can show "Added" inline. */
    val watchlistSymbols: StateFlow<Set<String>> = repository.watchlist
        .map { items -> items.map { it.symbol }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val uiState: StateFlow<SearchUiState> = kotlinx.coroutines.flow.combine(
        _query, _results, _isSearching, _notice, watchlistSymbols
    ) { query, results, searching, notice, watched ->
        SearchUiState(
            query = query,
            results = results,
            isSearching = searching,
            slotsUsed = watched.size,
            notice = notice,
            justAdded = _justAdded.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    val justAdded: StateFlow<String?> = _justAdded.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Local filtering is instant; the network leg is debounced so a handful
        // of dictated characters do not become a handful of requests.
        viewModelScope.launch {
            _query
                .debounce(320)
                .distinctUntilChanged()
                .collect { query -> runSearch(query) }
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
        _notice.value = null
        // Paint the offline catalogue immediately, then let the remote merge in.
        _results.value = SymbolCatalog.filter(query)
    }

    fun clearQuery() = onQueryChange("")

    private fun runSearch(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _isSearching.value = false
            _results.value = SymbolCatalog.filter(query)
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val merged = repository.search(query)
            if (merged.isNotEmpty()) _results.value = merged
            _isSearching.value = false
        }
    }

    fun add(symbol: String) {
        viewModelScope.launch {
            when (repository.addToWatchlist(symbol)) {
                StockRepository.AddResult.Added -> {
                    _notice.value = null
                    _justAdded.value = symbol
                }
                StockRepository.AddResult.AlreadyPresent ->
                    _notice.value = "Already on your watchlist"
                StockRepository.AddResult.Full ->
                    _notice.value = "Watchlist full — remove a stock first"
            }
        }
    }

    fun consumeJustAdded() { _justAdded.value = null }

    override fun onCleared() {
        searchJob?.cancel()
        super.onCleared()
    }
}
