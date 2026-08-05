package com.snib.app.ui.setup

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snib.app.AppState
import com.snib.app.apps.Permissions
import com.snib.app.data.SessionRepository
import com.snib.app.household.HouseholdKey
import com.snib.app.nfc.TapMode
import com.snib.app.recovery.RecoveryCodes
import com.snib.app.ui.BlockScreen
import com.snib.app.ui.BlocklistScreen
import com.snib.app.ui.CodeSheet
import com.snib.app.ui.SnibMark
import com.snib.app.ui.components.Notice
import com.snib.app.ui.components.PrimaryButton
import com.snib.app.ui.components.SecondaryButton
import com.snib.app.ui.theme.Snib
import kotlinx.coroutines.delay

/**
 * Welcome → apps → permissions → token → recovery codes → test snib.
 * One screen, one job. No account, no carousel, never four permission requests at once.
 */
@Composable
fun SetupFlow(onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val notice by AppState.notice.collectAsState()

    // Only the token step wants a tap to mean something other than snib/unsnib.
    LaunchedEffect(step) { if (step != 3) AppState.expect(TapMode.Toggle) }

    when (step) {
        0 -> Welcome { step = 1 }
        1 -> PickApps { step = 2 }
        2 -> PermissionSteps { step = 3 }
        3 -> FirstToken(notice) { step = 4 }
        4 -> Codes { step = 5 }
        5 -> TestSnib(onFinished)
    }
}

@Composable
private fun Frame(
    title: String,
    body: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Snib.harl)
            if (body != null) {
                Spacer(Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = Snib.harlDim)
            }
        }
        content()
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SnibMark(snibbed = true, modifier = Modifier.size(96.dp), animated = false)
        Spacer(Modifier.height(40.dp))
        Text(
            "Tap the token to snib your phone. Tap it again to open up.",
            style = MaterialTheme.typography.headlineMedium,
            color = Snib.harl,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        PrimaryButton("Set up Snib", onClick = onNext)
    }
}

@Composable
private fun PickApps(onNext: () -> Unit) {
    val blocked by SessionRepository.blocked.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { BlocklistScreen(onBack = null) }
        Box(Modifier.padding(24.dp)) {
            PrimaryButton(
                if (blocked.isEmpty()) "Pick at least one app" else "Next",
                enabled = blocked.isNotEmpty(),
                onClick = onNext,
            )
        }
    }
}

private data class Ask(
    val title: String,
    val why: String,
    val optional: Boolean,
    val granted: () -> Boolean,
    val intent: () -> Intent?,
)

@Composable
private fun PermissionSteps(onNext: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    val asks = remember {
        listOf(
            Ask(
                title = "Let Snib see which app is opening",
                why = "This is the only way one app on Android can block another. Snib reads " +
                    "nothing else and sends nothing anywhere. Everything stays on this phone.",
                optional = false,
                granted = { Permissions.accessibilityEnabled(context) },
                intent = { Permissions.accessibilityIntent() },
            ),
            Ask(
                title = "Let Snib draw over other apps",
                why = "The block screen appears on top of the app you opened, instead of " +
                    "bouncing you home without explanation.",
                optional = false,
                granted = { Permissions.overlayGranted(context) },
                intent = { Permissions.overlayIntent(context) },
            ),
            Ask(
                title = "Let Snib show a notification",
                why = "One quiet line while you're snibbed, so you always know the state of " +
                    "your phone without opening anything.",
                optional = false,
                granted = { Permissions.notificationsGranted(context) },
                intent = { null },
            ),
            Ask(
                title = "Let Snib sort your apps by use",
                why = "The apps worth blocking are the ones you already use most. Without this " +
                    "the list is alphabetical.",
                optional = true,
                granted = { com.snib.app.apps.InstalledApps.hasUsageAccess(context) },
                intent = { Permissions.usageAccessIntent() },
            ),
            Ask(
                title = "Keep Snib running in the background",
                why = "So a snib survives a restart, and your phone's battery manager doesn't " +
                    "quietly end your session.",
                optional = true,
                granted = { Permissions.batteryExempt(context) },
                intent = { Permissions.batteryIntent(context) },
            ),
            Ask(
                title = "Make Snib harder to undo",
                why = "Adds a deliberate extra step before Snib can be uninstalled. Snib cannot " +
                    "lock, wipe or manage your phone, and you can turn this off at any time.",
                optional = true,
                granted = { Permissions.deviceAdminActive(context) },
                intent = { Permissions.deviceAdminIntent(context) },
            ),
        )
    }

    // Cheap poll: these are all granted in Settings, which gives us no callback.
    LaunchedEffect(index) {
        while (true) {
            delay(700)
            tick++
        }
    }

    val ask = asks[index]
    val granted = remember(tick, index) { ask.granted() }

    Frame(title = ask.title, body = ask.why) {
        Column {
            if (granted) {
                Notice("Done.", tone = Snib.verdigris)
                Spacer(Modifier.height(16.dp))
            }
            PrimaryButton(if (granted) "Next" else "Open settings") {
                when {
                    granted && index < asks.lastIndex -> index++
                    granted -> onNext()
                    ask.intent() == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> ask.intent()?.let { context.startActivity(it) }
                }
            }
            if (ask.optional && !granted) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Skip this one") {
                    if (index < asks.lastIndex) index++ else onNext()
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "${index + 1} of ${asks.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Snib.harlFaint,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FirstToken(notice: String?, onNext: () -> Unit) {
    val context = LocalContext.current
    var joining by remember { mutableStateOf(false) }
    val tokenCount = remember(notice) { SessionRepository.dao.tokenCountNow() }
    val ready = HouseholdKey.exists(context) && tokenCount > 0

    LaunchedEffect(joining) {
        AppState.expect(if (joining) TapMode.JoinHousehold else TapMode.WriteNewToken)
    }

    Frame(
        title = if (joining) "Tap a token that already works" else "Hold a blank tag against the phone",
        body = if (joining) {
            "Your tokens will work on this phone too, and this phone's tokens will work on theirs."
        } else {
            "The tag becomes your first token. Add as many as you like later — one by the desk, " +
                "one by the bed."
        },
    ) {
        Column {
            if (notice != null) {
                Notice(notice)
                Spacer(Modifier.height(16.dp))
            }
            PrimaryButton(if (ready) "Next" else "Waiting for a tag", enabled = ready, onClick = onNext)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                if (joining) "Start my own household instead" else "Join someone else's household"
            ) { joining = !joining }
        }
    }
}

@Composable
private fun Codes(onNext: () -> Unit) {
    val context = LocalContext.current
    val codes = remember { RecoveryCodes.generate(context) }
    var written by remember { mutableStateOf(false) }

    Frame(
        title = "Write these down",
        body = "Eight codes. Each unsnibs your phone once, without a token. This is the only " +
            "time they are shown.",
    ) {
        Column {
            CodeSheet(codes)
            Spacer(Modifier.height(16.dp))
            SecondaryButton("Print or share") { share(context, codes) }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Snib.slateLift, RoundedCornerShape(12.dp))
                    .clickable { written = !written }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = written,
                    onCheckedChange = { written = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Snib.brass,
                        uncheckedColor = Snib.harlFaint,
                        checkmarkColor = Snib.slate,
                    ),
                )
                Text(
                    "I've written these down",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Snib.harl,
                )
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Next", enabled = written, onClick = onNext)
        }
    }
}

@Composable
private fun TestSnib(onFinished: () -> Unit) {
    var showing by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(5) }

    if (showing) {
        LaunchedEffect(Unit) {
            while (seconds > 0) {
                delay(1_000)
                seconds--
            }
            showing = false
        }
        // A forced five-second demo so the user sees the block screen before trusting it.
        // This is where trust is built. Don't cut it.
        BlockScreen(appLabel = "Instagram", onDismiss = {}, stranded = false)
        return
    }

    Frame(
        title = if (seconds == 0) "That's the whole product" else "See it work",
        body = if (seconds == 0) {
            "Tap your token to snib. Tap it again to open up. Nothing else on this screen can " +
                "let you back in."
        } else {
            "Here's what a blocked app looks like. Five seconds, so you know what you're " +
                "trusting before you trust it."
        },
    ) {
        if (seconds == 0) {
            PrimaryButton("Done", onClick = onFinished)
        } else {
            PrimaryButton("Show me") { showing = true }
        }
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
