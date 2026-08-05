package com.snib.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snib.app.data.SessionRepository
import com.snib.app.recovery.RecoveryCodes
import com.snib.app.service.BlockingService
import com.snib.app.service.SessionService
import com.snib.app.ui.components.Notice
import com.snib.app.ui.components.PrimaryButton
import com.snib.app.ui.components.ScreenHeader
import com.snib.app.ui.components.SecondaryButton
import com.snib.app.ui.theme.Snib

/** Shown exactly once at setup, and again only when deliberately regenerated. */
@Composable
fun CodeSheet(codes: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Snib.slateLift, RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        codes.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                pair.forEach { code ->
                    Text(
                        code,
                        style = MaterialTheme.typography.titleMedium,
                        color = Snib.harl,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun RecoveryCodesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var codes by remember { mutableStateOf<List<String>>(emptyList()) }
    val unused by SessionRepository.dao.unusedCodeCountFlow().collectAsState(initial = 0)
    val canRegenerate = RecoveryCodes.canRegenerate()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        ScreenHeader("Recovery codes", onBack)

        Text(
            "Eight single-use codes. Each one fully unsnibs this phone. " +
                "Keep them somewhere that isn't your phone.",
            style = MaterialTheme.typography.labelSmall,
            color = Snib.harlDim,
        )
        Spacer(Modifier.height(20.dp))
        Text("$unused unused", style = MaterialTheme.typography.headlineMedium, color = Snib.harl)
        Spacer(Modifier.height(24.dp))

        if (codes.isNotEmpty()) {
            CodeSheet(codes)
            Spacer(Modifier.height(16.dp))
            SecondaryButton("Print or share") { share(context, codes) }
            Spacer(Modifier.height(12.dp))
            Notice("These are shown once. The old codes stopped working the moment these appeared.")
        } else if (canRegenerate) {
            PrimaryButton("Generate new codes") { codes = RecoveryCodes.generate(context) }
            Spacer(Modifier.height(12.dp))
            Text(
                "Generating replaces all eight.",
                style = MaterialTheme.typography.labelSmall,
                color = Snib.harlFaint,
            )
        } else {
            Notice("Codes can only be replaced while your phone is open.")
        }
    }
}

/** The wallet-card answer: token's at home, you're in Glasgow, you need your banking app. */
@Composable
fun RedeemCodeScreen(onBack: () -> Unit, onRedeemed: () -> Unit) {
    val context = LocalContext.current
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        ScreenHeader("Use a recovery code", onBack)

        Text(
            "One of the eight codes from setup. Each works once.",
            style = MaterialTheme.typography.labelSmall,
            color = Snib.harlDim,
        )
        Spacer(Modifier.height(24.dp))

        TextField(
            value = entry,
            onValueChange = { entry = it.uppercase(); error = null },
            singleLine = true,
            placeholder = { Text("XXXX-XXXX", color = Snib.harlFaint) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.titleMedium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Snib.slateLift,
                unfocusedContainerColor = Snib.slateLift,
                focusedIndicatorColor = Snib.rust,
                unfocusedIndicatorColor = Snib.slateLift,
                focusedTextColor = Snib.harl,
                unfocusedTextColor = Snib.harl,
                cursorColor = Snib.rust,
            ),
        )

        Spacer(Modifier.height(20.dp))

        PrimaryButton("Unsnib", tone = Snib.rust, enabled = entry.isNotBlank()) {
            if (RecoveryCodes.redeem(context, entry)) {
                BlockingService.clearBlock()
                SessionService.sync(context)
                onRedeemed()
            } else {
                error = "That code doesn't match, or it has already been used."
                entry = ""
            }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Notice(error!!, tone = Snib.rust)
        }
    }
}

@Composable
fun BreakScreen(onBack: () -> Unit, onStarted: () -> Unit) {
    val context = LocalContext.current
    val remaining = remember { com.snib.app.recovery.Breaks.remaining(SessionRepository.dao) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader("Emergency break", onBack)

        Text(
            "Five minutes. " + com.snib.app.recovery.Breaks.costSentence(remaining),
            style = MaterialTheme.typography.bodyLarge,
            color = Snib.harl,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))

        com.snib.app.ui.components.HoldToConfirm(label = "Hold") {
            if (SessionRepository.startBreak()) {
                SessionService.sync(context)
                BlockingService.clearBlock()
                onStarted()
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Hold for five seconds. Let go to cancel.",
            style = MaterialTheme.typography.labelSmall,
            color = Snib.harlFaint,
            textAlign = TextAlign.Center,
        )
    }
}

private fun share(context: android.content.Context, codes: List<String>) {
    val text = buildString {
        appendLine("Snib recovery codes")
        appendLine("Each code unsnibs your phone once. Keep this somewhere that isn't your phone.")
        appendLine()
        codes.forEach { appendLine(it) }
    }
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            "Recovery codes",
        )
    )
}
