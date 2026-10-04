package com.siddharth.stockticker.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.siddharth.stockticker.ui.theme.AppColors

/**
 * First-load state. The indicator is already a ring, so it belongs on a round
 * screen without extra dressing — and it is centred so that when data lands the
 * content appears where the eye already is.
 */
@Composable
fun LoadingState(
    message: String = "Loading prices",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(38.dp),
            strokeWidth = 3.dp,
            indicatorColor = AppColors.accent,
            trackColor = AppColors.border
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.caption1,
            color = AppColors.textMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Failure with nothing cached to fall back on. One plain-language line and one
 * big retry target — never an exception message, never a dead end.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "!",
            style = MaterialTheme.typography.display3,
            color = AppColors.negative
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 3
        )
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.caption2,
                color = AppColors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
        Spacer(Modifier.height(12.dp))
        Chip(
            onClick = onRetry,
            label = {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.button,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            colors = ChipDefaults.primaryChipColors(
                backgroundColor = AppColors.accent,
                contentColor = MaterialTheme.colors.onBackground
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Empty watchlist. Not a blank circle: a prompt with the next step attached. */
@Composable
fun EmptyWatchlistState(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No stocks yet",
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Add up to 5 to watch",
            style = MaterialTheme.typography.caption2,
            color = AppColors.textMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Chip(
            onClick = onAdd,
            label = {
                Text(
                    text = "Search stocks",
                    style = MaterialTheme.typography.button,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            colors = ChipDefaults.primaryChipColors(
                backgroundColor = AppColors.accent,
                contentColor = MaterialTheme.colors.onBackground
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Centred one-liner, used for empty search results. */
@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption1,
            color = AppColors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 3
        )
    }
}

/**
 * Background-refresh marker: a slow pulsing dot, shown only while content is
 * already on screen. A full-screen spinner over data the user can already read
 * is a regression, not feedback.
 *
 * The animation is created only while [visible] is true, so nothing animates
 * off-screen or after the refresh settles.
 */
@Composable
fun RefreshPulse(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) {
        Spacer(modifier.size(6.dp))
        return
    }
    val transition = rememberInfiniteTransition(label = "refreshPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(760),
            repeatMode = RepeatMode.Reverse
        ),
        label = "refreshPulseAlpha"
    )
    Box(
        modifier = modifier
            .size(6.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(AppColors.accent)
    )
}

/** Inline, non-blocking notice — "watchlist full", "showing cached prices". */
@Composable
fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(
                if (isWarning) AppColors.negative.copy(alpha = 0.16f)
                else AppColors.surfaceElevated
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            color = if (isWarning) AppColors.negative else AppColors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
