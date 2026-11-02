package com.snib.app.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.snib.app.apps.InstalledApp
import com.snib.app.apps.InstalledApps
import com.snib.app.data.SessionRepository
import com.snib.app.ui.components.Notice
import com.snib.app.ui.components.ScreenHeader
import com.snib.app.ui.theme.Snib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A scrolling checkbox list: icon, name, checkbox. Search at the top.
 * No categories, no presets, no per-app stats.
 */
@Composable
fun BlocklistScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val blocked by SessionRepository.blocked.collectAsState()
    val editable = !SessionRepository.isSnibbed
    var query by remember { mutableStateOf("") }

    val apps by produceState(initialValue = emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }

    val (allowed, choosable) = apps.partition { it.protected }
    val visible = choosable.filter { it.label.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        ScreenHeader("Apps", onBack)

        if (!editable) {
            Notice("Your phone is snibbed. Apps can be changed once you tap your token.")
            Spacer(Modifier.height(16.dp))
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search", color = Snib.harlFaint) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Snib.slateLift,
                unfocusedContainerColor = Snib.slateLift,
                focusedIndicatorColor = Snib.brass,
                unfocusedIndicatorColor = Snib.slateLift,
                focusedTextColor = Snib.harl,
                unfocusedTextColor = Snib.harl,
                cursorColor = Snib.brass,
            ),
        )

        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = { it.packageName }) { app ->
                val checked = app.packageName in blocked
                AppRow(
                    app = app,
                    checked = checked,
                    enabled = editable,
                    onToggle = { SessionRepository.setBlocked(app.packageName, it) },
                )
                if (app.isBrowser && checked) {
                    Text(
                        "A browser opens everything you've snibbed, on the web.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Snib.harlFaint,
                        modifier = Modifier.padding(start = 54.dp, bottom = 8.dp),
                    )
                }
            }

            if (query.isBlank() && allowed.isNotEmpty()) {
                item {
                    Text(
                        "Always allowed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Snib.harlDim,
                        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
                    )
                    Text(
                        "The dialler, messages, emergency calling and Settings can never be " +
                            "snibbed. Neither can Snib itself.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Snib.harlFaint,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(allowed, key = { "protected-" + it.packageName }) { app ->
                    AppRow(app = app, checked = false, enabled = false, onToggle = {})
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val icon = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(Snib.slateLift, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (app.protected) Snib.harlFaint else Snib.harl,
            )
        }
        if (!app.protected) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle(it) },
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = Snib.brass,
                    uncheckedColor = Snib.harlFaint,
                    checkmarkColor = Snib.slate,
                ),
            )
        }
    }
}
