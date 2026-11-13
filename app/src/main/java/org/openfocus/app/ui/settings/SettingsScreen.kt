package org.openfocus.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.shield.ToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val c = appContainer()
    val s by c.settings.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleRow("Gentle mode", "Missed habits and abandoned sessions don't cost HP. Recommended while you build momentum.", s.gentleMode) { on ->
                c.settings.update { it.copy(gentleMode = on) }
            }
            ToggleRow("Strict mode", "No paid unlocks while a focus session is running.", s.strictMode) { on ->
                c.settings.update { it.copy(strictMode = on) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Evening reminder", style = MaterialTheme.typography.titleSmall)
                    Text("If habits are still open after this hour", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { c.settings.update { it.copy(reminderHour = (it.reminderHour - 1).coerceAtLeast(12)) } }) { Icon(Icons.Filled.Remove, "Earlier") }
                Text("%02d:00".format(s.reminderHour), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { c.settings.update { it.copy(reminderHour = (it.reminderHour + 1).coerceAtMost(23)) } }) { Icon(Icons.Filled.Add, "Later") }
            }
            HorizontalDivider()
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "OpenFocus is free and open-source software (GPL-3.0-or-later). Everything stays on your device — " +
                    "no accounts, no analytics, no network access.\n\n" +
                    "Fonts: Nunito and Fredoka, SIL Open Font License 1.1.\n" +
                    "Icons: Material Icons by Google, Apache License 2.0.\n" +
                    "Hero, mountain and launcher art are original placeholders (CC0).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
