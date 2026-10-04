package com.siddharth.stockticker.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val WearColors = Colors(
    primary = Accent,
    primaryVariant = Accent,
    secondary = Positive,
    secondaryVariant = Positive,
    background = Background,
    surface = Surface,
    error = Negative,
    onPrimary = TextPrimary,
    onSecondary = Background,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    onError = TextPrimary
)

/** Dark-only by design (AMOLED power + glance legibility). */
@Composable
fun StockTickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColors,
        typography = AppTypography,
        content = content
    )
}
