package com.siddharth.stockticker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.siddharth.stockticker.ui.theme.AppColors

/**
 * The chart's period selector: 1D / 1W / 1M / 3M / 6M / 1Y.
 *
 * Six labels have to share the widest line of a round screen, so each tab is
 * sized by weight rather than content — equal slices, no reflow when the
 * selection moves, and the row stays centred on the circle. The tabs are
 * intentionally full-height touch strips: the visible pill is small, but the
 * hit area runs the whole height of the row so a near miss still lands.
 */
@Composable
fun PeriodTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (isSelected) AppColors.accent else Color.Transparent,
                animationSpec = tween(180),
                label = "tabBackground"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colors.onBackground
                } else {
                    AppColors.textMuted
                },
                animationSpec = tween(180),
                label = "tabText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .pointerInput(index, selectedIndex) {
                        detectTapGestures(onTap = {
                            if (index != selectedIndex) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(index)
                            }
                        })
                    }
                    .clearAndSetSemantics {
                        contentDescription = label
                        selected = isSelected
                        onClick(label = "Show $label") { onSelect(index); true }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(background)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
