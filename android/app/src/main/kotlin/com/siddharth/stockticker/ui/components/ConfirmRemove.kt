package com.siddharth.stockticker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.siddharth.stockticker.ui.theme.AppColors
import com.siddharth.stockticker.util.Formatters

/**
 * Removal confirmation as a full screen, not a dialog.
 *
 * A modal with two small buttons is the single worst control to hit on a watch.
 * Here the whole display is the decision: one line of question, two 52dp round
 * targets side by side, and swipe-left still cancels because the screen sits
 * inside the navigation host.
 */
@Composable
fun ConfirmRemoveScreen(
    symbol: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Remove",
            style = MaterialTheme.typography.caption1,
            color = AppColors.textMuted,
            textAlign = TextAlign.Center
        )
        Text(
            text = Formatters.displaySymbol(symbol),
            style = MaterialTheme.typography.display3,
            color = MaterialTheme.colors.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = "from your watchlist?",
            style = MaterialTheme.typography.caption2,
            color = AppColors.textMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.secondaryButtonColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.onSurface
                ),
                modifier = Modifier.size(52.dp)
            ) {
                Text(text = "✕", style = MaterialTheme.typography.title2)
            }
            Spacer(Modifier.width(18.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.primaryButtonColors(
                    backgroundColor = AppColors.negative,
                    contentColor = MaterialTheme.colors.onBackground
                ),
                modifier = Modifier.size(52.dp)
            ) {
                Text(text = "✓", style = MaterialTheme.typography.title2)
            }
        }
    }
}
