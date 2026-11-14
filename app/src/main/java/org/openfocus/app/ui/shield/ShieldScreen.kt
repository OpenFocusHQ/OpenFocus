package org.openfocus.app.ui.shield

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.openfocus.app.blocking.BlockerAccessibilityService
import org.openfocus.app.core.Days
import org.openfocus.app.core.GameRules
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.data.db.BlockMode
import org.openfocus.app.data.db.BlockRuleEntity
import org.openfocus.app.data.db.BlockScheduleEntity
import org.openfocus.app.data.prefs.ShortsPlatform
import org.openfocus.app.ui.components.SectionHeader
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.components.formatMinuteOfDay
import org.openfocus.app.ui.theme.Success

@Composable
fun ShieldScreen(nav: NavController) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.state.collectAsState()
    val rules by c.blocks.rules.collectAsState(initial = emptyList())
    val schedules by c.blocks.schedules.collectAsState(initial = emptyList())
    var a11y by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(false) }
    var editSchedule by remember { mutableStateOf<BlockScheduleEntity?>(null) }
    LifecycleResumeEffect(Unit) {
        a11y = BlockerAccessibilityService.isEnabled(context)
        usage = c.usage.hasPermission()
        onPauseOrDispose { }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Shield", style = MaterialTheme.typography.headlineMedium)
            Text("Block distracting apps and endless short-video feeds.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            PermissionCard(
                ok = a11y, title = "Accessibility service",
                body = "Required. Lets OpenFocus see which app is open so it can step in. Nothing is recorded or sent anywhere.",
                action = "Open settings",
            ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        item {
            PermissionCard(
                ok = usage, title = "Usage access",
                body = "Optional. Needed for daily time limits.",
                action = "Grant",
            ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        item {
            ToggleRow("Shield active", "Pause scheduled & always-on blocks. Focus sessions still block.", !settings.shieldPaused) { on ->
                c.settings.update { it.copy(shieldPaused = !on) }
            }
        }
        item {
            ToggleRow("Strict mode", "During focus sessions every block is final: no waiting through, no coin unlocks.", settings.strictMode) { on ->
                c.settings.update { it.copy(strictMode = on) }
            }
        }

        item { SectionHeader("Short-form video blocker") }
        items(ShortsPlatform.entries) { p ->
            ToggleRow(p.label, p.note, p in settings.shorts) { on ->
                c.settings.update { it.copy(shorts = if (on) it.shorts + p else it.shorts - p) }
            }
        }

        item {
            SectionHeader("Blocked apps") {
                TextButton(onClick = { nav.navigate("apps/block") }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text("Add") }
            }
        }
        if (rules.isEmpty()) item {
            Text("No apps yet. Add the ones that steal your time — they'll be blocked during focus sessions and schedules.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(rules, key = { it.packageName }) { r ->
            RuleRow(r, usageMinutes = if (usage) c.usage.todayMinutes(r.packageName) else null,
                onChange = { scope.launch { c.blocks.upsertRule(it) } },
                onRemove = { scope.launch { c.blocks.removeRule(r.packageName) } })
        }

        item {
            SectionHeader("Schedules") {
                TextButton(onClick = { editSchedule = BlockScheduleEntity(name = "Evening wind-down", startMinute = 21 * 60, endMinute = 7 * 60) }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text("Add")
                }
            }
        }
        if (schedules.isEmpty()) item {
            Text("Schedules block your app list automatically, e.g. work hours or bedtime.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(schedules, key = { it.id }) { s ->
            Card(onClick = { editSchedule = s }, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall)
                        Text("${formatMinuteOfDay(s.startMinute)}–${formatMinuteOfDay(s.endMinute)} · " +
                            Days.shortNames.filterIndexed { i, _ -> Days.maskHas(s.daysMask, i) }.joinToString(" "),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(s.enabled, { on -> scope.launch { c.blocks.upsertSchedule(s.copy(enabled = on)) } })
                }
            }
        }
    }

    editSchedule?.let { s ->
        ScheduleDialog(s, onDismiss = { editSchedule = null }, onSave = { scope.launch { c.blocks.upsertSchedule(it) }; editSchedule = null },
            onDelete = { scope.launch { c.blocks.deleteSchedule(s) }; editSchedule = null })
    }
}

@Composable
private fun PermissionCard(ok: Boolean, title: String, body: String, action: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null, tint = if (ok) Success else MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            if (!ok) FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun RuleRow(r: BlockRuleEntity, usageMinutes: Int?, onChange: (BlockRuleEntity) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = MaterialTheme.typography.titleSmall)
                    usageMinutes?.let { Text("${it}m used today", style = MaterialTheme.typography.bodySmall) }
                }
                Switch(r.enabled, { onChange(r.copy(enabled = it)) })
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove") }
            }
            Box {
                OutlinedButton(onClick = { menu = true }) {
                    Text(
                        when (r.mode) {
                            BlockMode.ALWAYS -> "Always blocked"
                            BlockMode.DAILY_LIMIT -> "Daily limit: ${r.dailyLimitMinutes} min"
                            BlockMode.FOCUS_AND_SCHEDULE -> "During focus & schedules"
                        },
                    )
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("During focus & schedules") }, onClick = { menu = false; onChange(r.copy(mode = BlockMode.FOCUS_AND_SCHEDULE)) })
                    listOf(15, 30, 60, 90).forEach { m ->
                        DropdownMenuItem(text = { Text("Daily limit: $m min") }, onClick = { menu = false; onChange(r.copy(mode = BlockMode.DAILY_LIMIT, dailyLimitMinutes = m)) })
                    }
                    DropdownMenuItem(text = { Text("Always blocked") }, onClick = { menu = false; onChange(r.copy(mode = BlockMode.ALWAYS)) })
                }
            }
            GateRow(r, onChange)
        }
    }
}

/** "When blocked": fully blocked, or a wait timer that then lets the app open for a while. */
@Composable
private fun GateRow(r: BlockRuleEntity, onChange: (BlockRuleEntity) -> Unit) {
    var gateMenu by remember { mutableStateOf(false) }
    var allowMenu by remember { mutableStateOf(false) }
    fun waitLabel(s: Int) = if (s < 60) "${s}s" else "${s / 60} min"
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("When blocked:", style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { gateMenu = true }) {
                Text(if (r.gate == BlockGate.HARD) "Fully blocked" else "Wait ${waitLabel(r.waitSeconds)}")
            }
            DropdownMenu(gateMenu, { gateMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Fully blocked") },
                    onClick = { gateMenu = false; onChange(r.copy(gate = BlockGate.HARD)) },
                )
                GameRules.waitOptionsSeconds.forEach { s ->
                    DropdownMenuItem(
                        text = { Text("Wait ${waitLabel(s)}, then continue") },
                        onClick = { gateMenu = false; onChange(r.copy(gate = BlockGate.WAIT, waitSeconds = s)) },
                    )
                }
            }
        }
        if (r.gate == BlockGate.WAIT) {
            Box {
                TextButton(onClick = { allowMenu = true }) { Text("for ${r.allowMinutes} min") }
                DropdownMenu(allowMenu, { allowMenu = false }) {
                    GameRules.allowOptionsMinutes.forEach { m ->
                        DropdownMenuItem(text = { Text("Open for $m min") }, onClick = { allowMenu = false; onChange(r.copy(allowMinutes = m)) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(s: BlockScheduleEntity, onDismiss: () -> Unit, onSave: (BlockScheduleEntity) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var days by remember { mutableIntStateOf(s.daysMask) }
    var editing by remember { mutableStateOf<String?>(null) }
    var start by remember { mutableIntStateOf(s.startMinute) }
    var end by remember { mutableIntStateOf(s.endMinute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (editing != null) {
                    val init = if (editing == "start") start else end
                    val tp = rememberTimePickerState(init / 60, init % 60, true)
                    TimePicker(tp)
                    TextButton(onClick = {
                        val m = tp.hour * 60 + tp.minute
                        if (editing == "start") start = m else end = m
                        editing = null
                    }) { Text("Set") }
                } else {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = "start" }) { Text("From ${formatMinuteOfDay(start)}") }
                        OutlinedButton(onClick = { editing = "end" }) { Text("To ${formatMinuteOfDay(end)}") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Days.shortNames.forEachIndexed { i, n ->
                            FilterChip(Days.maskHas(days, i), { days = days xor (1 shl i) }, label = { Text(n) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = editing == null && days != 0 && start != end,
                onClick = { onSave(s.copy(name = name.ifBlank { "Schedule" }, startMinute = start, endMinute = end, daysMask = days)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete") } },
    )
}
