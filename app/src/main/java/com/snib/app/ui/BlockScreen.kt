package com.snib.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snib.app.recovery.StrandedUnlock
import com.snib.app.ui.theme.Snib
import com.snib.app.ui.theme.SnibTheme

/**
 * Full slate, mark vertical. One dismiss, returning home.
 *
 * No countdown, and no count of failed attempts — counting turns a moment of failure into a score.
 */
@Composable
fun BlockScreen(
    appLabel: String,
    onDismiss: () -> Unit,
    stranded: Boolean = StrandedUnlock.isStranded(),
    onStrandedUnlock: () -> Unit = {},
) {
    SnibTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Snib.slate)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SnibMark(snibbed = true, modifier = Modifier.size(96.dp), animated = false)

                Text(
                    "$appLabel is snibbed.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Snib.harl,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp),
                )
                Text(
                    "Tap your token to open up.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Snib.harlDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Column(
                Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (stranded) {
                    Text(
                        "Stranded without a token",
                        style = MaterialTheme.typography.labelSmall,
                        color = Snib.rust,
                        modifier = Modifier
                            .clickable(onClick = onStrandedUnlock)
                            .padding(vertical = 12.dp),
                    )
                }
                Text(
                    "Dismiss",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Snib.harlDim,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 16.dp, horizontal = 32.dp),
                )
            }
        }
    }
}
