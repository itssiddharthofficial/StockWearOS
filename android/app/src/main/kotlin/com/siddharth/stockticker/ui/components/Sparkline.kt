package com.siddharth.stockticker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bare trend line for a list row: no axes, no labels, no fill.
 *
 * At this size a sparkline can only answer one question — which way has this
 * been going — so everything that would compete with that is left out. The
 * line is drawn against its own min/max so the shape fills the box regardless
 * of the price level, and it is coloured by net direction over the window so it
 * agrees with the percentage sitting next to it.
 *
 * With fewer than two usable points it draws a flat baseline rather than
 * nothing, so rows keep a consistent height while data is still arriving.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 18.dp,
    strokeWidth: Dp = 1.5.dp
) {
    Canvas(modifier = modifier.size(width = width, height = height)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val usableWidth = size.width - stroke
        val usableHeight = size.height - stroke

        if (values.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.45f),
                start = Offset(inset, size.height / 2f),
                end = Offset(size.width - inset, size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val lowest = values.min()
        val highest = values.max()
        // A perfectly flat series would divide by zero; draw it down the middle.
        val span = (highest - lowest).takeIf { it > 0.0 }

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = inset + usableWidth * index / (values.size - 1).toFloat()
            val y = if (span == null) {
                size.height / 2f
            } else {
                inset + ((highest - value) / span * usableHeight).toFloat()
            }
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
