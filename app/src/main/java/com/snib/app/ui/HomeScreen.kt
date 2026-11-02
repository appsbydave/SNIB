package com.snib.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snib.app.data.SessionRepository
import com.snib.app.ui.components.Notice
import com.snib.app.ui.components.QuietRow
import com.snib.app.ui.theme.Snib
import kotlinx.coroutines.delay

/**
 * Snibbed: the mark, elapsed time in display type, remaining breaks in small text. Nothing else.
 * No charts, no counters, no streaks.
 */
@Composable
fun HomeScreen(
    notice: String?,
    blockedCount: Int,
    onOpenBlocklist: () -> Unit,
    onOpenTokens: () -> Unit,
    onOpenCodes: () -> Unit,
    onTakeBreak: () -> Unit,
    onUseCode: () -> Unit,
) {
    val state by SessionRepository.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val snibbed = state.snibbedAt != null
    val breakRemaining = remember(now, snibbed) { SessionRepository.breakRemainingMs() }
    val onBreak = breakRemaining > 0L
    val breaksLeft = remember(now / 60_000, snibbed) { SessionRepository.breaksRemaining() }

    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SnibMark(snibbed = snibbed, modifier = Modifier.size(112.dp))

            Spacer(Modifier.height(28.dp))

            if (snibbed) {
                Text(
                    elapsed(state.snibbedAt!!, now),
                    style = MaterialTheme.typography.displayLarge,
                    color = Snib.harl,
                )
                Text(
                    "SNIBBED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Snib.brass,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    "Not snibbed",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Snib.harl,
                )
                Text(
                    "Tap your token to snib.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Snib.harlDim,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (onBreak) {
                Spacer(Modifier.height(28.dp))
                Notice(
                    "Emergency break. Snib comes back on in ${countdown(breakRemaining)}.",
                    tone = Snib.rust,
                )
            }

            if (!snibbed) {
                Spacer(Modifier.height(40.dp))
                Column(Modifier.fillMaxWidth()) {
                    QuietRow("Apps", "$blockedCount blocked", onOpenBlocklist)
                    QuietRow("Tokens", null, onOpenTokens)
                    QuietRow("Recovery codes", null, onOpenCodes)
                }
            }
        }

        if (notice != null) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 24.dp)) {
                Notice(notice)
            }
        }

        // Buried as small text at the foot of the home screen, not a button. Both routes out
        // are deliberately quiet, and deliberately reachable — a phone must never trap you.
        if (snibbed) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!onBreak) {
                    Text(
                        text = if (breaksLeft > 0) {
                            "Emergency break — $breaksLeft left this week"
                        } else {
                            "No emergency breaks left until Monday"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Snib.harlFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable(enabled = breaksLeft > 0, onClick = onTakeBreak)
                            .padding(vertical = 10.dp),
                    )
                }
                Text(
                    "Use a recovery code",
                    style = MaterialTheme.typography.labelSmall,
                    color = Snib.harlFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(onClick = onUseCode).padding(vertical = 10.dp),
                )
            }
        }
    }
}

private fun elapsed(from: Long, now: Long): String {
    val seconds = ((now - from) / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "%dh %02dm".format(hours, minutes) else "%dm".format(minutes)
}

private fun countdown(remainingMs: Long): String {
    val seconds = (remainingMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
