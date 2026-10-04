package com.siddharth.stockticker.ui.detail

import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.siddharth.stockticker.data.model.ChartPeriod
import com.siddharth.stockticker.ui.components.PriceLineChart
import com.siddharth.stockticker.ui.components.ErrorState
import com.siddharth.stockticker.ui.components.LoadingState
import com.siddharth.stockticker.ui.components.MetricRow
import com.siddharth.stockticker.ui.components.PeriodTabs
import com.siddharth.stockticker.ui.components.ReturnRow
import com.siddharth.stockticker.ui.components.SectionHeader
import com.siddharth.stockticker.ui.theme.AppColors
import com.siddharth.stockticker.util.Formatters
import kotlinx.coroutines.launch

/** The four content pages, in order. */
private enum class DetailPage {
    CHART,
    FUNDAMENTALS,
    RETURNS,
    RISK
}

/**
 * Detail screen: four horizontally paged sections, each with its own vertical
 * scroll.
 *
 * Splitting the sections across pages rather than stacking them into one long
 * column keeps each page to roughly one screenful, so the dot indicator is a
 * real map of where you are instead of a scrollbar on a column that never ends.
 *
 * `edgeSwipeToDismiss` reserves only the left edge for "back", so a horizontal
 * swipe anywhere else pages between sections. The chart consumes its own
 * horizontal drags for scrubbing, so the pager never sees those.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun DetailScreen(
    initialSymbol: String,
    swipeToDismissBoxState: SwipeToDismissBoxState,
    viewModel: DetailViewModel = viewModel()
) {
    val symbols by viewModel.symbols.collectAsStateWithLifecycle()
    val currentSymbol by viewModel.symbol.collectAsStateWithLifecycle()

    LaunchedEffect(initialSymbol) { viewModel.select(initialSymbol) }

    val symbol = currentSymbol ?: initialSymbol
    val stock by viewModel.observe(symbol).collectAsStateWithLifecycle(initialValue = null)
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()

    val state = DetailUiState(
        symbol = symbol,
        stock = stock,
        isRefreshing = isRefreshing,
        error = error
    )

    val pagerState = rememberPagerState(pageCount = { DetailPage.entries.size })
    val pageIndicatorState = remember(pagerState) {
        object : PageIndicatorState {
            override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int get() = pagerState.currentPage
            override val pageCount: Int get() = DetailPage.entries.size
        }
    }

    val scope = rememberCoroutineScope()
    val currentIndex = symbols.indexOf(symbol)

    // Switching stock returns to the Chart page: landing deep in the Risk page
    // of a stock you just moved to would be disorienting.
    fun goToSymbol(delta: Int) {
        if (currentIndex < 0) return
        val target = (currentIndex + delta).coerceIn(0, symbols.lastIndex)
        if (target == currentIndex) return
        viewModel.select(symbols[target])
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    if (state.stock == null) {
        Box(Modifier.fillMaxSize()) {
            TimeText()
            if (state.error != null) {
                ErrorState(message = state.error.orEmpty(), onRetry = viewModel::retry)
            } else {
                LoadingState(message = Formatters.displaySymbol(symbol))
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .edgeSwipeToDismiss(swipeToDismissBoxState),
            beyondViewportPageCount = 1
        ) { pageIndex ->
            when (DetailPage.entries[pageIndex]) {
                DetailPage.CHART -> ChartPage(
                    state = state,
                    chart = chart,
                    isActive = pageIndex == pagerState.currentPage,
                    onPeriodSelect = viewModel::setPeriod,
                    onPointSelect = viewModel::selectPoint,
                    onRotary = viewModel::nudgePeriod
                )

                DetailPage.FUNDAMENTALS -> FundamentalsPage(state)

                DetailPage.RETURNS -> ReturnsPage(state)

                DetailPage.RISK -> RiskPage(
                    state = state,
                    canGoPrev = currentIndex > 0,
                    canGoNext = currentIndex in 0 until symbols.lastIndex,
                    onGo = { delta -> goToSymbol(delta) }
                )
            }
        }

        HorizontalPageIndicator(pageIndicatorState = pageIndicatorState)
    }
}

// ---- Page 1: Chart ------------------------------------------------------

@Composable
private fun ChartPage(
    state: DetailUiState,
    chart: ChartState,
    isActive: Boolean,
    onPeriodSelect: (ChartPeriod) -> Unit,
    onPointSelect: (Int) -> Unit,
    onRotary: (Int) -> Unit
) {
    // Not a scrolling list: the chart is the whole point of this page, and a
    // page that opens with its chart below the fold has failed. Everything here
    // is sized to fit one screenful, so there is nothing to scroll to.
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val shortEdge = minOf(maxWidth, maxHeight)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryPeriod(isActive, onRotary)
                    // Deeper side inset than the other pages use: the candle
                    // row sits low on the circle, where the usable chord is
                    // narrower than it is at the vertical centre.
                    .padding(
                        start = shortEdge * 0.115f,
                        end = shortEdge * 0.115f,
                        top = shortEdge * 0.085f,
                        bottom = shortEdge * 0.105f
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CompactPriceHeader(state)
                Spacer(Modifier.height(4.dp))

                PeriodTabs(
                    labels = ChartPeriod.labels,
                    selectedIndex = chart.period.ordinal,
                    onSelect = { index ->
                        ChartPeriod.entries.getOrNull(index)?.let(onPeriodSelect)
                    }
                )

                if (chart.isLoading && chart.points.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading chart",
                            fontSize = 11.sp,
                            color = AppColors.textMuted
                        )
                    }
                } else {
                    PriceLineChart(
                        points = chart.points,
                        period = chart.period,
                        selectedIndex = chart.selectedIndex,
                        onSelect = onPointSelect,
                        chartHeight = 64.dp
                    )
                }
            }
        }
    }
}

/**
 * Symbol, price and day change on two tight lines.
 *
 * Deliberately smaller than the price block on a phone would be: on this page
 * the headline number is competing with the chart for the same screenful, and
 * the chart is what the user navigated here for.
 */
@Composable
private fun CompactPriceHeader(state: DetailUiState) {
    val stock = state.stock ?: return
    val accent = AppColors.forChange(stock.changePercent)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = Formatters.displaySymbol(stock.symbol),
            fontSize = 10.sp,
            color = AppColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = Formatters.price(stock.price),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = Formatters.percent(stock.changePercent),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

// ---- Page 2: Fundamentals ----------------------------------------------

@Composable
private fun FundamentalsPage(state: DetailUiState) {
    val stock = state.stock ?: return
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )
    PageScaffold(listState) { shortEdge ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Not auto-centred: the first item on these pages is a one-line
            // status caption, and centring on it would push the actual content
            // into the bottom half of the circle with a blank top half.
            contentPadding = PaddingValues(
                start = shortEdge * 0.105f,
                end = shortEdge * 0.105f,
                top = shortEdge * 0.165f,
                bottom = shortEdge * 0.16f
            ),
            autoCentering = null
        ) {
            item { UpdatedAt(stock.quoteUpdatedAt) }
            item { SectionHeader("Fundamentals") }
            item {
                MetricRow(
                    leftLabel = "Mkt cap",
                    leftValue = Formatters.marketCap(stock.marketCap),
                    rightLabel = "P/E",
                    rightValue = Formatters.ratio(stock.peRatio)
                )
            }
            item {
                MetricRow(
                    leftLabel = "Div yield",
                    leftValue = stock.dividendYield?.let {
                        Formatters.decimal(it, 2) + "%"
                    } ?: Formatters.DASH,
                    rightLabel = "52W high",
                    rightValue = Formatters.priceCompact(stock.week52High)
                )
            }
            item {
                MetricRow(
                    leftLabel = "Day low",
                    leftValue = Formatters.priceCompact(stock.dayLow),
                    rightLabel = "Day high",
                    rightValue = Formatters.priceCompact(stock.dayHigh)
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

// ---- Page 3: Returns ----------------------------------------------------

@Composable
private fun ReturnsPage(state: DetailUiState) {
    val stock = state.stock ?: return
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )
    PageScaffold(listState) { shortEdge ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Not auto-centred: the first item on these pages is a one-line
            // status caption, and centring on it would push the actual content
            // into the bottom half of the circle with a blank top half.
            contentPadding = PaddingValues(
                start = shortEdge * 0.105f,
                end = shortEdge * 0.105f,
                top = shortEdge * 0.165f,
                bottom = shortEdge * 0.16f
            ),
            autoCentering = null
        ) {
            item { UpdatedAt(stock.quoteUpdatedAt) }
            item { SectionHeader("Returns") }
            state.returns.forEach { row ->
                item {
                    ReturnRow(
                        label = row.label,
                        value = row.value,
                        valueText = Formatters.percent(row.value, decimals = 1),
                        maxMagnitude = state.returnsScale
                    )
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

// ---- Page 4: Risk -------------------------------------------------------

@Composable
private fun RiskPage(
    state: DetailUiState,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onGo: (Int) -> Unit
) {
    val stock = state.stock ?: return
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )
    PageScaffold(listState) { shortEdge ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Not auto-centred: the first item on these pages is a one-line
            // status caption, and centring on it would push the actual content
            // into the bottom half of the circle with a blank top half.
            contentPadding = PaddingValues(
                start = shortEdge * 0.105f,
                end = shortEdge * 0.105f,
                top = shortEdge * 0.165f,
                bottom = shortEdge * 0.16f
            ),
            autoCentering = null
        ) {
            item { UpdatedAt(stock.quoteUpdatedAt) }
            item { SectionHeader("Risk") }
            item {
                MetricRow(
                    leftLabel = "Beta vs Nifty",
                    leftValue = Formatters.decimal(stock.beta, 2),
                    rightLabel = "Volatility",
                    rightValue = stock.volatility?.let {
                        Formatters.decimal(it, 1) + "%"
                    } ?: Formatters.DASH
                )
            }
            item {
                MetricRow(
                    leftLabel = "52W low",
                    leftValue = Formatters.priceCompact(stock.week52Low),
                    rightLabel = "Volume",
                    rightValue = Formatters.compactCount(stock.volume)
                )
            }
            item {
                Text(
                    text = "Returns, beta and volatility are computed on " +
                        "device from weekly price history.",
                    fontSize = 9.sp,
                    color = AppColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Moving between watchlist stocks lives here, because the pager now
            // belongs to the four content pages.
            if (canGoPrev || canGoNext) {
                item { SectionHeader("Watchlist") }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NavChip(
                            label = "Prev",
                            enabled = canGoPrev,
                            onClick = { onGo(-1) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        NavChip(
                            label = "Next",
                            enabled = canGoNext,
                            onClick = { onGo(1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

// ---- Shared page chrome -------------------------------------------------

/**
 * Clock, vignette and scroll indicator for one page. Each page owns its own
 * [ScalingLazyListState], so scrolling one does not move the others.
 */
@Composable
private fun PageScaffold(
    listState: ScalingLazyListState,
    content: @Composable (shortEdge: Dp) -> Unit
) {
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            content(minOf(maxWidth, maxHeight))
        }
    }
}

/** "Updated 3m ago", at the top of every page but the chart. */
@Composable
private fun UpdatedAt(epochMillis: Long) {
    if (epochMillis <= 0L) return
    Text(
        text = "Updated " + Formatters.relativeTime(epochMillis),
        fontSize = 9.sp,
        color = AppColors.textMuted,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NavChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.button,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface
        ),
        modifier = modifier
    )
}

/**
 * Crown/bezel rotation steps the chart period. The chart page is short enough
 * not to need rotary scrolling, so the rotary is spent on the control that
 * actually has a range.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.rotaryPeriod(isActive: Boolean, onRotary: (Int) -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isActive) {
        if (isActive) runCatching { focusRequester.requestFocus() }
    }
    return this
        .onRotaryScrollEvent { event ->
            onRotary(if (event.verticalScrollPixels > 0) 1 else -1)
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
