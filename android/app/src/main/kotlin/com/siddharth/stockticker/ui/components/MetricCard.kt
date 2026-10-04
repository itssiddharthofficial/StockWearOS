package com.siddharth.stockticker.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.siddharth.stockticker.ui.theme.AppColors

/**
 * A single fundamentals tile.
 *
 * Generously rounded and inset on all sides: a hard-cornered card on a round
 * screen looks like a mistake, and the inset keeps every tile inside the
 * bezel-safe area rather than trusting the edge.
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colors.onSurface
) {
    Column(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.caption3,
            color = AppColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Two tiles side by side, which is the widest a readable pair gets on 466px. */
@Composable
fun MetricRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    modifier: Modifier = Modifier,
    leftColor: Color = MaterialTheme.colors.onSurface,
    rightColor: Color = MaterialTheme.colors.onSurface
) {
    Row(modifier = modifier.fillMaxWidth()) {
        MetricCard(
            label = leftLabel,
            value = leftValue,
            valueColor = leftColor,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        MetricCard(
            label = rightLabel,
            value = rightValue,
            valueColor = rightColor,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * A labelled value with a signed bar behind it — used for the returns table.
 * The bar is scaled against the largest magnitude in the group, so a 3% move
 * and a 300% move are both legible instead of one flattening the other.
 */
@Composable
fun ReturnRow(
    label: String,
    value: Double?,
    valueText: String,
    maxMagnitude: Double,
    modifier: Modifier = Modifier
) {
    val colour = AppColors.forChange(value)
    val fraction = if (value == null || maxMagnitude <= 0.0) {
        0f
    } else {
        (kotlin.math.abs(value) / maxMagnitude).coerceIn(0.0, 1.0).toFloat()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colors.surface)
    ) {
        // Intensity bar: fills from the left, tinted by direction.
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(30.dp)
                .background(colour.copy(alpha = 0.16f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption1,
                color = AppColors.textMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.body2,
                color = colour,
                textAlign = TextAlign.End
            )
        }
    }
}

/** A section heading inside the scrolling panel. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.caption3,
        color = AppColors.textMuted,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}
