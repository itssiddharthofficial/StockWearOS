package com.siddharth.stockticker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.siddharth.stockticker.data.repository.StockRepository
import com.siddharth.stockticker.ui.components.ConfirmRemoveScreen
import com.siddharth.stockticker.ui.components.EmptyWatchlistState
import com.siddharth.stockticker.ui.components.ErrorState
import com.siddharth.stockticker.ui.components.LoadingState
import com.siddharth.stockticker.ui.components.RefreshPulse
import com.siddharth.stockticker.ui.components.SearchGlyph
import com.siddharth.stockticker.ui.components.StockListRow
import com.siddharth.stockticker.ui.theme.AppColors
import com.siddharth.stockticker.util.Formatters
import kotlinx.coroutines.launch

/**
 * Home: the watchlist as a vertical list of stock rows.
 *
 * A `ScalingLazyColumn` rather than a plain list — it scales and fades rows as
 * they approach the top and bottom of the circle, so nothing is ever abruptly
 * clipped by the curve, and it drives the position indicator on the right edge.
 *
 * The summary sits at the top of the scroll rather than pinned over it: a fixed
 * header would eat a third of a 1.4" screen permanently, and the portfolio
 * number is something you read on arrival, not something you need while
 * scrolling. Tapping it refreshes; the search button next to it opens search.
 */
@Composable
fun HomeScreen(
    onStockClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val removalTarget by viewModel.removalTarget.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Freshness that matters is freshness when looked at, so every return to
    // this screen refreshes rather than relying on the 15-minute worker floor.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh(force = false)
        onPauseOrDispose { }
    }

    removalTarget?.let { symbol ->
        ConfirmRemoveScreen(
            symbol = symbol,
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.confirmRemoval()
            },
            onCancel = viewModel::cancelRemoval
        )
        return
    }

    when {
        state.isLoadingFirstTime -> Box(Modifier.fillMaxSize()) {
            TimeText()
            LoadingState()
        }

        state.isEmpty -> Box(Modifier.fillMaxSize()) {
            TimeText()
            EmptyWatchlistState(onAdd = onSearchClick)
        }

        state.items.none { it.stock?.price != null } && state.error != null ->
            Box(Modifier.fillMaxSize()) {
                TimeText()
                ErrorState(
                    message = state.error.orEmpty(),
                    hint = if (!state.isOnline) "No network connection" else null,
                    onRetry = { viewModel.refresh(force = true) }
                )
            }

        else -> WatchlistList(
            state = state,
            onStockClick = onStockClick,
            onLongPress = viewModel::askToRemove,
            onSearchClick = onSearchClick,
            onRefresh = { viewModel.refresh(force = true) }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WatchlistList(
    state: HomeUiState,
    onStockClick: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        onPauseOrDispose { }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Fractions of the shorter edge, never pixels: correct on a 450px
            // or 480px round face as well as a 466px one.
            val shortEdge = minOf(maxWidth, maxHeight)

            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        scope.launch { listState.animateScrollBy(event.verticalScrollPixels) }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally,
                // The first item sits high in the circle where the chord is
                // narrowest, so the top inset is deeper than the side inset
                // rather than equal to it.
                contentPadding = PaddingValues(
                    start = shortEdge * 0.075f,
                    end = shortEdge * 0.075f,
                    top = shortEdge * 0.21f,
                    bottom = shortEdge * 0.17f
                ),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                autoCentering = null
            ) {
                item {
                    PortfolioSummary(
                        changePercent = state.aggregateChangePercent,
                        isRefreshing = state.isRefreshing,
                        lastUpdatedAt = state.lastUpdatedAt,
                        isStale = state.isStale,
                        isOffline = !state.isOnline,
                        onRefresh = onRefresh,
                        onSearch = onSearchClick
                    )
                }

                items(state.items, key = { it.symbol }) { item ->
                    StockListRow(
                        symbol = item.symbol,
                        name = item.name,
                        price = item.price,
                        changePercent = item.changePercent,
                        trend = item.trend,
                        isDegraded = item.isDegraded,
                        onClick = { onStockClick(item.symbol) },
                        onLongClick = { onLongPress(item.symbol) }
                    )
                }

                if (state.items.size < StockRepository.MAX_WATCHLIST) {
                    item { AddStockRow(onClick = onSearchClick) }
                }

                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

/**
 * The portfolio line: equal-weighted average day move, a freshness caption and
 * a search button. Tapping the number refreshes — the gesture people try first.
 */
@Composable
private fun PortfolioSummary(
    changePercent: Double?,
    isRefreshing: Boolean,
    lastUpdatedAt: Long?,
    isStale: Boolean,
    isOffline: Boolean,
    onRefresh: () -> Unit,
    onSearch: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val accent = AppColors.forChange(changePercent)

    val caption = when {
        isOffline && lastUpdatedAt != null ->
            "offline · " + Formatters.relativeTime(lastUpdatedAt)
        isOffline -> "offline"
        isStale && lastUpdatedAt != null -> Formatters.relativeTime(lastUpdatedAt)
        else -> "today"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefresh()
                    })
                }
                .clearAndSetSemantics {
                    contentDescription = "Watchlist average " +
                        Formatters.percent(changePercent) + ". Tap to refresh."
                    onClick(label = "Refresh") { onRefresh(); true }
                }
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "WATCHLIST",
                    fontSize = 9.sp,
                    color = AppColors.textMuted,
                    maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
                RefreshPulse(visible = isRefreshing)
            }
            Text(
                text = Formatters.percent(changePercent),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
            )
            Text(
                text = caption,
                fontSize = 9.sp,
                color = if (isOffline || isStale) AppColors.neutral else AppColors.textMuted,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colors.surface)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSearch()
                    })
                }
                .clearAndSetSemantics {
                    contentDescription = "Search stocks"
                    onClick(label = "Search stocks") { onSearch(); true }
                },
            contentAlignment = Alignment.Center
        ) {
            SearchGlyph(color = AppColors.textMuted, size = 20.dp)
        }
    }
}

/** Trailing row shown while the watchlist has room, so the cap stays visible. */
@Composable
private fun AddStockRow(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.45f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                })
            }
            .clearAndSetSemantics {
                contentDescription = "Add a stock"
                onClick(label = "Add a stock") { onClick(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+  Add stock",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.textMuted,
            textAlign = TextAlign.Center
        )
    }
}
