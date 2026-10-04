package com.siddharth.stockticker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.siddharth.stockticker.ui.theme.AppColors
import com.siddharth.stockticker.util.Formatters

/**
 * One watchlist stock as a list row.
 *
 * The row carries four things in a fixed grid so the eye can scan straight down
 * a column rather than re-finding each field per row: symbol and price on the
 * left, trend and percentage on the right.
 *
 * The sparkline is coloured by its **own** net direction, not by the day change
 * beside it. They cover different windows — a month of closes versus today — so
 * tying them to one colour would paint a line that visibly slopes down in the
 * green of a good day. Letting each carry its own sign means a red line next to
 * a green percentage reads correctly: down over the month, up today.
 *
 * A row with no price yet renders greyed with an em dash rather than vanishing,
 * so one failed symbol degrades to one dull row instead of a gap in the list.
 */
@Composable
fun StockListRow(
    symbol: String,
    name: String?,
    price: Double?,
    changePercent: Double?,
    trend: List<Double>,
    isDegraded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val accent = if (isDegraded) AppColors.neutral else AppColors.forChange(changePercent)
    val trendColor = when {
        isDegraded || trend.size < 2 -> AppColors.neutral
        else -> AppColors.forChange(trend.last() - trend.first())
    }

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(110),
        label = "rowPress"
    )

    val spoken = buildString {
        append(Formatters.displaySymbol(symbol))
        append(", ")
        if (isDegraded) {
            append("no data")
        } else {
            append(Formatters.price(price))
            append(", ")
            append(Formatters.percent(changePercent))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            // 52dp clears the 48dp platform minimum with room for two text lines.
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colors.surface)
            .pointerInput(symbol) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
            }
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(label = "Open details") { onClick(); true }
                onLongClick(label = "Remove from watchlist") { onLongClick(); true }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = Formatters.displaySymbol(symbol),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isDegraded) {
                        name ?: Formatters.DASH
                    } else {
                        Formatters.price(price)
                    },
                    fontSize = 11.sp,
                    color = AppColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Sparkline(values = trend, color = trendColor)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isDegraded) {
                        Formatters.DASH
                    } else {
                        Formatters.percentTight(changePercent)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
