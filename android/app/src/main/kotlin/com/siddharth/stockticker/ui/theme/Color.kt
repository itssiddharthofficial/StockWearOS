package com.siddharth.stockticker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for colour. Composables read these through
 * [androidx.wear.compose.material.MaterialTheme.colors] or [AppColors]; nothing
 * in the UI layer should spell out a hex value.
 */
val Background = Color(0xFF0A0A0A)
val Surface = Color(0xFF1A1A1A)
val SurfaceElevated = Color(0xFF232323)
val BorderColor = Color(0xFF333333)
val TextPrimary = Color(0xFFE2E8F0)
val TextMuted = Color(0xFF888888)
val Positive = Color(0xFF22C55E)
val Negative = Color(0xFFEF4444)
val Accent = Color(0xFF3B82F6)

/** Scrub/selection highlight. A state, not a price direction. */
val Selection = Color(0xFFF59E0B)
val Neutral = Color(0xFF6B7280)

/** Tokens that have no slot in Wear's [androidx.wear.compose.material.Colors]. */
object AppColors {
    val positive = Positive
    val negative = Negative
    val neutral = Neutral
    val accent = Accent
    val selection = Selection
    val border = BorderColor
    val textMuted = TextMuted
    val surfaceElevated = SurfaceElevated

    /** Green above zero, red below, grey when flat or unknown. */
    fun forChange(changePercent: Double?): Color = when {
        changePercent == null -> neutral
        changePercent > 0.0 -> positive
        changePercent < 0.0 -> negative
        else -> neutral
    }
}
