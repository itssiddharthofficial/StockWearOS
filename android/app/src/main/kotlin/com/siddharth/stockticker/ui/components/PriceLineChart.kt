package com.siddharth.stockticker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.siddharth.stockticker.data.model.ChartPeriod
import com.siddharth.stockticker.data.model.PricePoint
import com.siddharth.stockticker.ui.theme.AppColors
import com.siddharth.stockticker.util.Formatters

/**
 * Price line chart with a filled area, a mean reference line and drag-to-scrub.
 *
 * The line is one solid colour for the whole period — green when the period
 * closed up, red when it closed down — rather than changing colour mid-line.
 * A line that switches colour at every crossing reads as noise; the question
 * this chart answers at a glance is "up or down over this window", and one
 * colour answers it.
 *
 * Points are drawn as straight segments with no smoothing. That is deliberate:
 * real closes are jagged, and a spline through them would draw a pleasant curve
 * that no price ever traded at.
 *
 * Scrubbing uses `detectDragGestures` directly on the canvas. The chart page
 * does not scroll, so claiming both axes here costs nothing; paging between
 * sections is done above or below the chart.
 */
@Composable
fun PriceLineChart(
    points: List<PricePoint>,
    period: ChartPeriod,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 72.dp
) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(chartHeight + TOOLTIP_RESERVE + AXIS_RESERVE),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Chart unavailable",
                fontSize = 11.sp,
                color = AppColors.textMuted,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val first = points.first().price
    val last = points.last().price
    val lineColor = if (last >= first) AppColors.positive else AppColors.negative

    val lowest = points.minOf { it.price }
    val highest = points.maxOf { it.price }
    val mean = points.sumOf { it.price } / points.size

    val backgroundColor = MaterialTheme.colors.background
    val selected = selectedIndex?.coerceIn(0, points.lastIndex)
    val selectedPoint = selected?.let { points[it] }

    // Plot inset: keeps the first and last points off the extreme edge, where a
    // round screen has already curved away from the content box.
    val plotInset = 6.dp

    Column(modifier = modifier.fillMaxWidth()) {

        // --- Tooltip lane -------------------------------------------------
        // A fixed-height lane above the plot, so moving the marker never
        // reflows the chart below it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOOLTIP_RESERVE)
        ) {
            if (selectedPoint != null) {
                ScrubTooltip(
                    point = selectedPoint,
                    period = period,
                    fraction = selected.toFloat() / (points.size - 1).toFloat()
                )
            }
        }

        // --- Plot ----------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(points, period) {
                    fun pick(x: Float) {
                        val insetPx = with(density) { plotInset.toPx() }
                        val usable = (size.width - insetPx * 2f).coerceAtLeast(1f)
                        val fraction = ((x - insetPx) / usable).coerceIn(0f, 1f)
                        val index = Math.round(fraction * (points.size - 1))
                            .coerceIn(0, points.lastIndex)
                        if (index != selectedIndex) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                    detectDragGestures(
                        onDragStart = { offset -> pick(offset.x) },
                        onDrag = { change, _ ->
                            change.consume()
                            pick(change.position.x)
                        }
                    )
                }
                .pointerInput(points, period) {
                    detectTapGestures(
                        onTap = { offset ->
                            val insetPx = with(density) { plotInset.toPx() }
                            val usable = (size.width - insetPx * 2f).coerceAtLeast(1f)
                            val fraction = ((offset.x - insetPx) / usable).coerceIn(0f, 1f)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(
                                Math.round(fraction * (points.size - 1))
                                    .coerceIn(0, points.lastIndex)
                            )
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val insetPx = plotInset.toPx()
                val topPad = 3.dp.toPx()
                val bottomPad = 3.dp.toPx()
                val usableWidth = (size.width - insetPx * 2f).coerceAtLeast(1f)
                val usableHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                // A flat series would divide by zero; give it a nominal band so
                // the line sits mid-chart instead of collapsing onto an edge.
                val span = (highest - lowest).takeIf { it > 0.0 }
                    ?: (kotlin.math.abs(highest) * 0.02 + 1.0)

                fun xFor(index: Int): Float =
                    insetPx + usableWidth * index / (points.size - 1).toFloat()

                fun yFor(price: Double): Float =
                    topPad + ((highest - price) / span * usableHeight).toFloat()

                // Area fill: flat, same hue as the line, no gradient.
                val area = Path().apply {
                    moveTo(xFor(0), size.height - bottomPad)
                    points.forEachIndexed { index, point ->
                        lineTo(xFor(index), yFor(point.price))
                    }
                    lineTo(xFor(points.lastIndex), size.height - bottomPad)
                    close()
                }
                drawPath(path = area, color = lineColor.copy(alpha = 0.10f))

                // Mean reference line.
                drawLine(
                    color = AppColors.textMuted.copy(alpha = 0.55f),
                    start = Offset(insetPx, yFor(mean)),
                    end = Offset(size.width - insetPx, yFor(mean)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                    )
                )

                // The line itself: straight segments, no smoothing.
                val line = Path().apply {
                    points.forEachIndexed { index, point ->
                        val x = xFor(index)
                        val y = yFor(point.price)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(
                    path = line,
                    color = lineColor,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Scrub marker: vertical dashed guide plus a dot on the line.
                if (selected != null) {
                    val x = xFor(selected)
                    val y = yFor(points[selected].price)
                    drawLine(
                        color = AppColors.textMuted,
                        start = Offset(x, topPad),
                        end = Offset(x, size.height - bottomPad),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(3.dp.toPx(), 3.dp.toPx())
                        )
                    )
                    // Ringed in the background colour so the dot stays legible
                    // where it sits on top of the line.
                    drawCircle(
                        color = backgroundColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 2.6.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

        AxisLabels(points = points, period = period, inset = plotInset)
    }
}

/**
 * Floating readout for the scrubbed point.
 *
 * Tracks the finger horizontally but is clamped to the content width, so on a
 * round screen it can never slide out under the bezel at either end.
 */
@Composable
private fun ScrubTooltip(
    point: PricePoint,
    period: ChartPeriod,
    fraction: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 2.dp),
        contentAlignment = BiasedCenter(fraction)
    ) {
        Text(
            text = Formatters.chartLabel(point.epochSeconds, period) +
                "  •  " + Formatters.rupees(point.price),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(AppColors.surfaceElevated)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/**
 * Horizontal alignment at an arbitrary fraction of the parent, clamped so the
 * child never overhangs either edge.
 */
private data class BiasedCenter(val fraction: Float) : Alignment {
    override fun align(
        size: androidx.compose.ui.unit.IntSize,
        space: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection
    ): androidx.compose.ui.unit.IntOffset {
        val free = (space.width - size.width).coerceAtLeast(0)
        val x = (free * fraction.coerceIn(0f, 1f)).toInt()
        val y = (space.height - size.height).coerceAtLeast(0)
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

/**
 * Three muted labels under the plot: start, middle and end of the window.
 *
 * The outer two are pulled in by the same inset the plot uses, so they sit
 * under the first and last points rather than at the extreme edge of the box
 * where the circle has already curved away.
 */
@Composable
private fun AxisLabels(
    points: List<PricePoint>,
    period: ChartPeriod,
    inset: Dp
) {
    val middle = points[points.size / 2]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AXIS_RESERVE)
            .padding(horizontal = inset)
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        AxisLabel(Formatters.chartLabel(points.first().epochSeconds, period))
        AxisLabel(Formatters.chartLabel(middle.epochSeconds, period))
        AxisLabel(Formatters.chartLabel(points.last().epochSeconds, period))
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        fontSize = 8.sp,
        color = AppColors.textMuted,
        maxLines = 1
    )
}

/** Fixed lanes above and below the plot, so scrubbing never reflows the page. */
private val TOOLTIP_RESERVE = 20.dp
private val AXIS_RESERVE = 14.dp
