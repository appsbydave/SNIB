package com.snib.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snib.app.apps.InstalledApps
import com.snib.app.data.SessionRepository
import com.snib.app.ui.theme.Snib
import com.snib.app.ui.theme.SnibTheme
import kotlinx.coroutines.delay

/** One app the snibbed home screen can open. A name, nothing else: no icon, no badge. */
data class HomeLink(val packageName: String, val label: String)

object HomeLinks {

    /** The kept apps, alphabetical. */
    fun kept(context: Context, packages: Set<String>): List<HomeLink> =
        packages.mapNotNull { link(context, it) }.sortedBy { it.label.lowercase() }

    /**
     * The always-allowed apps a person actually reaches for: phone, messages, clock, Settings.
     * With [includeSnib], Snib too — breaks and recovery codes live on its own home screen.
     */
    fun always(context: Context, includeSnib: Boolean): List<HomeLink> {
        val packages = linkedSetOf<String>()
        InstalledApps.dialerPackage(context)?.let(packages::add)
        runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }
            .getOrNull()?.let(packages::add)
        InstalledApps.clockPackage(context)?.let(packages::add)
        InstalledApps.settingsPackage(context)?.let(packages::add)
        if (includeSnib) packages.add(context.packageName)
        return packages.mapNotNull { link(context, it) }
    }

    /**
     * Opens [packageName]'s launcher activity. Works from the accessibility service, which may
     * start activities from the background.
     */
    fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun link(context: Context, packageName: String): HomeLink? {
        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(packageName) == null) return null
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull() ?: return null
        return HomeLink(packageName, label)
    }
}

/**
 * The kept apps as plain text links, then the always-allowed few in small type. Shared by Snib's
 * own home screen and the snibbed home that covers the launcher, so the two never disagree.
 */
@Composable
fun KeptAppLinks(
    kept: List<HomeLink>,
    always: List<HomeLink>,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (kept.isEmpty()) {
            Text(
                "No apps kept. Choose up to five next time you're open.",
                style = MaterialTheme.typography.labelSmall,
                color = Snib.harlFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        kept.forEach { link ->
            Text(
                link.label,
                style = MaterialTheme.typography.titleMedium,
                color = Snib.harl,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { onOpen(link.packageName) }
                    .padding(horizontal = 28.dp, vertical = 10.dp),
            )
        }

        if (always.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            always.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.Center) {
                    row.forEach { link ->
                        Text(
                            link.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Snib.harlDim,
                            modifier = Modifier
                                .clickable { onOpen(link.packageName) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The home screen while snibbed, drawn over the launcher — and so over the app drawer and recents.
 * Swiping up lands here, not on a grid of every app. The mark, the time, and the kept apps.
 * Nothing else.
 */
@Composable
fun SnibbedHomeOverlay(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val state by SessionRepository.state.collectAsState()
    val allowed by SessionRepository.allowed.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val kept = remember(allowed) { HomeLinks.kept(context, allowed) }
    val always = remember { HomeLinks.always(context, includeSnib = true) }

    SnibTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Snib.slate)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SnibMark(snibbed = true, modifier = Modifier.size(72.dp), animated = false)
                Spacer(Modifier.height(24.dp))
                state.snibbedAt?.let { since ->
                    Text(
                        formatElapsed(since, now),
                        style = MaterialTheme.typography.displayLarge,
                        color = Snib.harl,
                    )
                }
                Text(
                    "SNIBBED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Snib.brass,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(36.dp))
                KeptAppLinks(kept = kept, always = always, onOpen = onOpen)
            }
        }
    }
}

internal fun formatElapsed(from: Long, now: Long): String {
    val seconds = ((now - from) / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "%dh %02dm".format(hours, minutes) else "%dm".format(minutes)
}
