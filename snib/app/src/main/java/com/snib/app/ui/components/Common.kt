package com.snib.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snib.app.ui.theme.Snib

/** Sentence case, active voice, one job each. A button says exactly what happens. */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Color = Snib.brass,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = tone,
            contentColor = Snib.slate,
            disabledContainerColor = Snib.slateLift,
            disabledContentColor = Snib.harlFaint,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Snib.harlFaint),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Snib.harl)
    }
}

/** A row of quiet text that goes somewhere. Used instead of buttons wherever weight would lie. */
@Composable
fun QuietRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Snib.harl)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelSmall, color = Snib.harlDim)
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)) {
        if (onBack != null) {
            Text(
                "Back",
                style = MaterialTheme.typography.labelSmall,
                color = Snib.harlDim,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Snib.harl)
    }
}

/** Errors explain and instruct. They don't apologise and they're never vague. */
@Composable
fun Notice(message: String, tone: Color = Snib.slateLift) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(tone, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            message,
            style = MaterialTheme.typography.labelSmall,
            color = Snib.harl,
            textAlign = TextAlign.Start,
        )
    }
}
