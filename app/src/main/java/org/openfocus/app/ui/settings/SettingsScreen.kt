package org.openfocus.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import org.openfocus.app.ui.shield.PauseConfigEditor
import org.openfocus.app.ui.shield.PauseConfig
import org.openfocus.app.core.GameRules
import org.openfocus.app.blocking.PauseState
import org.openfocus.app.blocking.DEFAULT_QUESTION
import org.openfocus.app.blocking.BlockReason
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
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
            ToggleRow("Strict mode", "During focus sessions every block is final: no waiting through, no coin unlocks.", s.strictMode) { on ->
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
            PauseScreenSettings()
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

/** Settings → Pause screen: defaults for new apps, coin prices and the message. */
@Composable
private fun PauseScreenSettings() {
    val c = appContainer()
    val s by c.settings.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Pause screen", style = MaterialTheme.typography.titleMedium)

        Text("Defaults for newly blocked apps", style = MaterialTheme.typography.titleSmall)
        PauseConfigEditor(
            PauseConfig(s.defaultGate, s.defaultWaitSeconds, s.defaultAllowMinutes),
            { cfg -> c.settings.update { it.copy(defaultGate = cfg.gate, defaultWaitSeconds = cfg.waitSeconds, defaultAllowMinutes = cfg.allowMinutes) } },
        )
        Text("Each app can be changed later in the Shield tab.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Coin prices", style = MaterialTheme.typography.titleSmall)
        PriceStepper("Skip a wait", "coins per 10 s of waiting (short waits count as 30 s)", s.skipCostPer10s) { v ->
            c.settings.update { it.copy(skipCostPer10s = v) }
        }
        PriceStepper("Unlock pass", "coins per minute unlocked", s.unlockCostPerMinute) { v ->
            c.settings.update { it.copy(unlockCostPerMinute = v) }
        }
        ToggleRow("Prices rise during the day", "Each paid unlock today makes the next one cost more.", s.escalatingPrices) { on ->
            c.settings.update { it.copy(escalatingPrices = on) }
        }
        Text(
            "Right now: skipping a ${GameRules.formatWait(s.defaultWaitSeconds)} wait costs ${s.skipCost(s.defaultWaitSeconds)} coins, " +
                "a 15 min pass costs ${s.unlockCost(15)}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Message", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            s.pauseQuestion, { v -> c.settings.update { it.copy(pauseQuestion = v.take(120)) } },
            Modifier.fillMaxWidth(),
            label = { Text("Question") },
            placeholder = { Text(DEFAULT_QUESTION) },
            supportingText = { Text("{app} is replaced with the app's name. Leave empty for the default.") },
        )
        OutlinedTextField(
            s.pauseReminder, { v -> c.settings.update { it.copy(pauseReminder = v.take(160)) } },
            Modifier.fillMaxWidth(),
            label = { Text("Personal reminder (optional)") },
            placeholder = { Text("e.g. You're training for the half marathon.") },
            supportingText = { Text("Shown under the question, instead of the usual encouragement.") },
        )
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(PauseState("Instagram", BlockReason.ALWAYS, question = s.pauseQuestion).headline,
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (s.pauseReminder.isNotBlank()) Text(s.pauseReminder, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PriceStepper(title: String, unit: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onChange((value - 1).coerceIn(GameRules.priceRange)) }, enabled = value > GameRules.priceRange.first) {
            Icon(Icons.Filled.Remove, "Cheaper")
        }
        Text(if (value == 0) "Free" else "$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange((value + 1).coerceIn(GameRules.priceRange)) }, enabled = value < GameRules.priceRange.last) {
            Icon(Icons.Filled.Add, "Pricier")
        }
    }
}
