package com.openfocushq.openfocus.ui.focus

import com.openfocushq.openfocus.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.openfocushq.openfocus.blocking.BlockerAccessibilityService
import com.openfocushq.openfocus.core.Days
import com.openfocushq.openfocus.core.GameRules
import com.openfocushq.openfocus.data.db.FocusOutcome
import com.openfocushq.openfocus.data.db.GoalStatus
import com.openfocushq.openfocus.data.prefs.ActiveFocus
import com.openfocushq.openfocus.ui.components.SectionHeader
import com.openfocushq.openfocus.ui.components.appContainer
import com.openfocushq.openfocus.ui.components.formatMinutes
import com.openfocushq.openfocus.ui.theme.NumberStyle
import com.openfocushq.openfocus.ui.theme.Success
import java.text.DateFormat
import java.util.Date

@Composable
fun FocusScreen(nav: NavController) {
    val c = appContainer()
    val active by c.focus.active.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.focus), style = MaterialTheme.typography.headlineMedium)
        val a = active
        if (a != null) RunningSession(a) else SessionSetup(nav)
        FocusStats()
    }
}

@Composable
private fun RunningSession(a: ActiveFocus) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(a.id) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val total = (a.endsAt - a.startedAt).coerceAtLeast(1)
    val left = (a.endsAt - now).coerceAtLeast(0)
    val frac = 1f - left.toFloat() / total
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primaryContainer

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (a.label.isNotBlank()) Text(a.label, style = MaterialTheme.typography.titleLarge)
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                val s = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = s, style = Stroke(stroke))
                drawArc(primary, -90f, 360f * frac, false, topLeft = Offset(inset, inset), size = s, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val secs = left / 1000
                Text("%d:%02d".format(secs / 60, secs % 60), style = NumberStyle)
                Text(if (a.deep) "Deep focus" else "Focus", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(stringResource(R.string.xp_waiting_at_the_finish_line, GameRules.focusXp(a.plannedMinutes, a.deep)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.put_the_phone_down_blocked_apps), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        OutlinedButton(onClick = { confirm = true }) { Text(stringResource(R.string.end_early)) }
    }
    if (confirm) {
        val gentle = c.settings.value.gentleMode
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.end_this_session)) },
            text = {
                Text(stringResource(R.string.you_ll_get_partial_credit_for) +
                    (if (gentle) "." else ", but lose ${GameRules.FOCUS_ABANDON_DAMAGE} HP.") +
                    "\n\nThe urge to quit usually passes in a minute or two.")
            },
            confirmButton = { TextButton(onClick = { confirm = false; scope.launch { c.focus.abandon() } }) { Text(stringResource(R.string.end_session)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.keep_going)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionSetup(nav: NavController) {
    val c = appContainer()
    val context = LocalContext.current
    val goals by remember { c.goals.observeGoals() }.collectAsState(initial = emptyList())
    val settings by c.settings.state.collectAsState()
    var minutes by remember { mutableFloatStateOf(25f) }
    var label by remember { mutableStateOf("") }
    var goalId by remember { mutableStateOf<String?>(null) }
    var deep by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(formatMinutes(minutes.toInt()), style = NumberStyle, modifier = Modifier.align(Alignment.CenterHorizontally))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 25, 45, 60, 90).forEach { m ->
                    FilterChip(selected = minutes.toInt() == m, onClick = { minutes = m.toFloat() }, label = { Text("$m") })
                }
            }
            Slider(minutes, { minutes = (Math.round(it / 5f) * 5f).coerceIn(5f, 180f) }, valueRange = 5f..180f)
            OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.what_are_you_working_on)) }, singleLine = true)
            val active = goals.filter { it.goal.status == GoalStatus.ACTIVE }
            if (active.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }) {
                        Text(stringResource(R.string.goal) + (active.firstOrNull { it.goal.id == goalId }?.goal?.title ?: "none"))
                    }
                    DropdownMenu(open, { open = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.none)) }, onClick = { goalId = null; open = false })
                        active.forEach { g -> DropdownMenuItem(text = { Text(g.goal.title) }, onClick = { goalId = g.goal.id; open = false }) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.deep_focus), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.block_every_app_except_your_allow), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(deep, { deep = it })
            }
            if (deep) TextButton(onClick = { nav.navigate("apps/allow") }) { Text(stringResource(R.string.allowed_apps, settings.focusAllowlist.size)) }
            if (!BlockerAccessibilityService.isEnabled(context)) {
                Text(stringResource(R.string.shield_is_off_so_apps_won), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    c.focus.start(minutes.toInt(), label.trim(), goalId, deep)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.start_focusing_xp, GameRules.focusXp(minutes.toInt(), deep))) }
        }
    }
}

@Composable
private fun FocusStats() {
    val c = appContainer()
    val weekStart = remember { Days.startOfDayMillis(Days.today() - 6) }
    val week by remember { c.focus.since(weekStart) }.collectAsState(initial = emptyList())
    val recent by c.focus.recent.collectAsState(initial = emptyList())
    val today = Days.today()
    val perDay = (0..6).map { i ->
        val day = today - 6 + i
        val start = Days.startOfDayMillis(day)
        val end = Days.startOfDayMillis(day + 1)
        week.filter { it.outcome == FocusOutcome.COMPLETED && it.startedAt in start until end }.sumOf { it.actualMinutes }
    }
    SectionHeader(stringResource(R.string.this_week, formatMinutes(perDay.sum())))
    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(30)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        perDay.forEachIndexed { i, m ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val h = size.height * (m.toFloat() / max)
                    drawRoundRect(color, topLeft = Offset(size.width * 0.2f, size.height - h), size = Size(size.width * 0.6f, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
                }
                Text(Days.shortNames[Days.dayIndex(today - 6 + i)].take(1), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (recent.isNotEmpty()) {
        SectionHeader(stringResource(R.string.recent_sessions))
        val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
        recent.take(10).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (s.outcome == FocusOutcome.COMPLETED) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null,
                    tint = if (s.outcome == FocusOutcome.COMPLETED) Success else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.label.ifBlank { "Focus session" }, style = MaterialTheme.typography.bodyLarge)
                    Text(fmt.format(Date(s.startedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatMinutes(s.actualMinutes), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
