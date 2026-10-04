package com.siddharth.stockticker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The magnifier, drawn rather than imported.
 *
 * One shape used by both the home affordance and the search pill, so the two
 * read as the same control. Drawing it costs no asset, no decode, and stays
 * crisp at any density — which matters on a screen this dense.
 */
@Composable
fun SearchGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val lensRadius = this.size.minDimension * 0.30f
        val lensCentre = Offset(this.size.width * 0.42f, this.size.height * 0.42f)
        drawCircle(
            color = color,
            radius = lensRadius,
            center = lensCentre,
            style = Stroke(width = stroke)
        )
        val reach = lensRadius * 0.72f
        drawLine(
            color = color,
            start = Offset(lensCentre.x + reach, lensCentre.y + reach),
            end = Offset(this.size.width * 0.86f, this.size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
