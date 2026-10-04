package com.siddharth.stockticker.ui.search

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.app.RemoteInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.input.RemoteInputIntentHelper
import com.siddharth.stockticker.data.repository.SymbolCatalog
import com.siddharth.stockticker.ui.components.EmptyMessage
import com.siddharth.stockticker.ui.components.InlineNotice
import com.siddharth.stockticker.ui.components.SearchGlyph
import com.siddharth.stockticker.ui.theme.AppColors

/**
 * Search: a pill that hands off to the platform's own input screen, over a
 * live-filtered list of results.
 *
 * It deliberately does not render its own text field. Wear's remote-input
 * activity gives dictation, handwriting and the system keyboard in one target —
 * all three are better than anything a cramped in-app field could offer, and
 * dictation is by far the fastest way to reach a symbol on a watch.
 *
 * The list is never empty on arrival: the on-device catalogue of liquid NSE
 * names is already showing, so a common stock can be added with no typing at
 * all, and search still works with no network.
 */
@Composable
fun SearchScreen(
    onDone: () -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val watched by viewModel.watchlistSymbols.collectAsStateWithLifecycle()
    val justAdded by viewModel.justAdded.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val listState = rememberScalingLazyListState()

    // Adding is the whole point of this screen, so a successful add returns home
    // rather than leaving the user to find their own way back.
    LaunchedEffect(justAdded) {
        if (justAdded != null) {
            viewModel.consumeJustAdded()
            onDone()
        }
    }

    val inputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val results = RemoteInput.getResultsFromIntent(data)
            val spoken = results?.getCharSequence(REMOTE_INPUT_KEY)?.toString()
            if (!spoken.isNullOrBlank()) viewModel.onQueryChange(spoken)
        }
    }

    fun launchInput() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel("Search stocks")
            .build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        runCatching { inputLauncher.launch(intent) }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 44.dp
            ),
            autoCentering = null
        ) {
            item {
                SearchPill(
                    query = state.query,
                    slotLabel = state.slotLabel,
                    onClick = { launchInput() },
                    onClear = viewModel::clearQuery
                )
            }

            if (state.notice != null) {
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        InlineNotice(text = state.notice.orEmpty())
                    }
                }
            } else if (state.isFull) {
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        InlineNotice(
                            text = "Watchlist full — remove a stock first",
                            isWarning = true
                        )
                    }
                }
            }

            if (state.results.isEmpty()) {
                item {
                    EmptyMessage(
                        text = if (state.query.isBlank()) {
                            "Tap the bar to search"
                        } else {
                            "No matches for \"${state.query}\""
                        }
                    )
                }
            } else {
                item {
                    Text(
                        text = if (state.query.isBlank()) "POPULAR" else "RESULTS",
                        style = MaterialTheme.typography.caption3,
                        color = AppColors.textMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, top = 6.dp)
                    )
                }
                items(state.results, key = { it.symbol }) { entry ->
                    ResultChip(
                        entry = entry,
                        isAdded = entry.symbol in watched,
                        isFull = state.isFull,
                        onClick = { viewModel.add(entry.symbol) }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** The search bar, shaped as a full pill so it sits honestly inside the circle. */
@Composable
private fun SearchPill(
    query: String,
    slotLabel: String,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = {
            Text(
                text = query.ifBlank { "Search" },
                style = MaterialTheme.typography.body2,
                color = if (query.isBlank()) AppColors.textMuted else MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = "$slotLabel slots used",
                style = MaterialTheme.typography.caption3,
                color = AppColors.textMuted,
                maxLines = 1
            )
        },
        icon = {
            SearchGlyph(color = AppColors.accent, size = 22.dp)
        },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface
        ),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    )

    if (query.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Chip(
                onClick = onClear,
                label = {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = ChipDefaults.secondaryChipColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = AppColors.textMuted
                ),
                modifier = Modifier.width(96.dp)
            )
        }
    }
}

@Composable
private fun ResultChip(
    entry: SymbolCatalog.Entry,
    isAdded: Boolean,
    isFull: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val enabled = !isAdded && !isFull
    Chip(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        label = {
            Text(
                text = com.siddharth.stockticker.util.Formatters.displaySymbol(entry.symbol),
                style = MaterialTheme.typography.title3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = if (isAdded) "Already added" else "${entry.name} · ${entry.exchange}",
                style = MaterialTheme.typography.caption3,
                color = if (isAdded) AppColors.positive else AppColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}

private const val REMOTE_INPUT_KEY = "stock_query"
