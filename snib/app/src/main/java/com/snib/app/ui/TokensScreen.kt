package com.snib.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snib.app.AppState
import com.snib.app.data.SessionRepository
import com.snib.app.nfc.TapMode
import com.snib.app.ui.components.Notice
import com.snib.app.ui.components.QuietRow
import com.snib.app.ui.components.ScreenHeader
import com.snib.app.ui.theme.Snib

/**
 * Adding a token: from an unsnibbed state, hold a blank tag against the phone.
 * Multiple tokens per household falls out of the design for free — one by the desk, one by the bed.
 */
@Composable
fun TokensScreen(notice: String?, onBack: () -> Unit) {
    val tokens by SessionRepository.dao.tokensFlow().collectAsState(initial = emptyList())

    // While this screen is open, the next blank tag becomes a token.
    DisposableEffect(Unit) {
        AppState.expect(TapMode.WriteNewToken)
        onDispose { AppState.expect(TapMode.Toggle) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        ScreenHeader("Tokens", onBack)

        Text(
            "Hold a blank tag against the back of the phone to make it a token. " +
                "Any token in your household works on any phone in it.",
            style = MaterialTheme.typography.labelSmall,
            color = Snib.harlDim,
        )

        Spacer(Modifier.height(24.dp))

        if (notice != null) {
            Notice(notice)
            Spacer(Modifier.height(16.dp))
        }

        if (tokens.isEmpty()) {
            Text(
                "No tokens yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = Snib.harlFaint,
            )
        } else {
            tokens.forEach { token ->
                QuietRow(
                    label = token.label,
                    detail = "added ${relativeDays(token.addedAt)}",
                    onClick = {},
                )
            }
        }
    }
}

private fun relativeDays(at: Long): String {
    val days = (System.currentTimeMillis() - at) / (24 * 60 * 60 * 1000)
    return when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }
}
