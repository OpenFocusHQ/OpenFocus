package org.openfocus.app.ui.shield

import org.openfocus.app.R
import androidx.compose.ui.res.stringResource
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
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.data.db.BlockMode
import org.openfocus.app.data.db.BlockRuleEntity
import org.openfocus.app.data.db.BlockScheduleEntity
import org.openfocus.app.data.prefs.ShortsPlatform
import org.openfocus.app.ui.components.AccessibilityDisclosureDialog
import org.openfocus.app.ui.components.SectionHeader
import org.openfocus.app.ui.components.openAccessibilitySettings
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
    var disclose by remember { mutableStateOf(false) }
    if (disclose) AccessibilityDisclosureDialog(onAgree = { disclose = false; openAccessibilitySettings(context) }, onDismiss = { disclose = false })
    LifecycleResumeEffect(Unit) {
        a11y = BlockerAccessibilityService.isEnabled(context)
        usage = c.usage.hasPermission()
        onPauseOrDispose { }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.shield), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.block_distracting_apps_and_endless_short), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            PermissionCard(
                ok = a11y, title = stringResource(R.string.accessibility_service),
                body = stringResource(R.string.required_lets_openfocus_see_which_app),
                action = "Open settings",
            ) { disclose = true }
        }
        item {
            PermissionCard(
                ok = usage, title = stringResource(R.string.usage_access),
                body = stringResource(R.string.optional_needed_for_daily_time_limits),
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

        item { SectionHeader(stringResource(R.string.short_form_video_blocker)) }
        items(ShortsPlatform.entries) { p ->
            ToggleRow(p.label, p.note, p in settings.shorts) { on ->
                c.settings.update { it.copy(shorts = if (on) it.shorts + p else it.shorts - p) }
            }
        }

        item {
            SectionHeader(stringResource(R.string.blocked_apps)) {
                TextButton(onClick = { nav.navigate("apps/block") }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text(stringResource(R.string.add)) }
            }
        }
        if (rules.isEmpty()) item {
            Text(stringResource(R.string.no_apps_yet_add_the_ones),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(rules, key = { it.packageName }) { r ->
            RuleRow(r, usageMinutes = if (usage) c.usage.todayMinutes(r.packageName) else null,
                onChange = { scope.launch { c.blocks.upsertRule(it) } },
                onRemove = { scope.launch { c.blocks.removeRule(r.packageName) } })
        }

        item {
            SectionHeader(stringResource(R.string.schedules)) {
                TextButton(onClick = { editSchedule = BlockScheduleEntity(name = "Evening wind-down", startMinute = 21 * 60, endMinute = 7 * 60) }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text(stringResource(R.string.add))
                }
            }
        }
        if (schedules.isEmpty()) item {
            Text(stringResource(R.string.schedules_block_your_app_list_automatically),
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
                    usageMinutes?.let { Text(stringResource(R.string.m_used_today, it), style = MaterialTheme.typography.bodySmall) }
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
                    DropdownMenuItem(text = { Text(stringResource(R.string.during_focus_schedules)) }, onClick = { menu = false; onChange(r.copy(mode = BlockMode.FOCUS_AND_SCHEDULE)) })
                    listOf(15, 30, 60, 90).forEach { m ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.daily_limit_min, m)) }, onClick = { menu = false; onChange(r.copy(mode = BlockMode.DAILY_LIMIT, dailyLimitMinutes = m)) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.always_blocked)) }, onClick = { menu = false; onChange(r.copy(mode = BlockMode.ALWAYS)) })
                }
            }
            GateRow(r, onChange)
        }
    }
}

/** One-line summary of the app's pause screen, with an editor dialog for exact times and coins. */
@Composable
private fun GateRow(r: BlockRuleEntity, onChange: (BlockRuleEntity) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val config = PauseConfig(r.gate, r.waitSeconds, r.allowMinutes)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pause_screen), style = MaterialTheme.typography.labelMedium)
            Text(config.summary() + if (r.allowCoins) " · coins allowed" else " · no coins",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.edit)) }
    }
    if (editing) {
        var draft by remember { mutableStateOf(config) }
        var coins by remember { mutableStateOf(r.allowCoins) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.pause_screen_for, r.label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PauseConfigEditor(draft, { draft = it })
                    ToggleRow(
                        "Allow coins",
                        if (draft.gate == BlockGate.WAIT) "Skip the wait with coins, for as long as you pay for."
                        else "Unlock with coins, for as long as you pay for.",
                        coins,
                    ) { coins = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    onChange(r.copy(gate = draft.gate, waitSeconds = draft.waitSeconds, allowMinutes = draft.allowMinutes, allowCoins = coins))
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
        )
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
        title = { Text(stringResource(R.string.schedule)) },
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
                    }) { Text(stringResource(R.string.set)) }
                } else {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = "start" }) { Text(stringResource(R.string.from, formatMinuteOfDay(start))) }
                        OutlinedButton(onClick = { editing = "end" }) { Text(stringResource(R.string.to, formatMinuteOfDay(end))) }
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
                onClick = { onSave(s.copy(name = name.ifBlank { "Schedule" }, startMinute = start, endMinute = end, daysMask = days)) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) } },
    )
}
