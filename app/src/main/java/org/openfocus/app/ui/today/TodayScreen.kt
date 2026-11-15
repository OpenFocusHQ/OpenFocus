package org.openfocus.app.ui.today

import org.openfocus.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.openfocus.app.blocking.BlockerAccessibilityService
import org.openfocus.app.core.Encouragement
import org.openfocus.app.data.db.GoalStatus
import org.openfocus.app.data.db.HabitFrequency
import org.openfocus.app.ui.quests.TodayQuestCard
import org.openfocus.app.ui.components.EmptyState
import org.openfocus.app.ui.pixel.SpriteCatalog
import org.openfocus.app.ui.components.HeroCard
import org.openfocus.app.ui.components.SectionHeader
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.components.appViewModel
import org.openfocus.app.ui.habits.HabitRow
import org.openfocus.app.ui.habits.HabitsViewModel
import org.openfocus.app.ui.theme.paletteColor
import java.time.LocalTime

@Composable
fun TodayScreen(nav: NavController) {
    val c = appContainer()
    val vm = appViewModel { HabitsViewModel(it) }
    val habits by vm.habits.collectAsState()
    val profile by remember { c.db.profileDao().observe() }.collectAsState(initial = null)
    val goals by remember { c.goals.observeGoals() }.collectAsState(initial = emptyList())
    val focus by c.focus.active.collectAsState()
    val questBoard by remember { c.quests.observeBoard() }.collectAsState(initial = null)
    val settings by c.settings.state.collectAsState()
    val context = LocalContext.current
    var shieldOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        shieldOn = BlockerAccessibilityService.isEnabled(context)
        onPauseOrDispose { }
    }

    val due = habits.orEmpty().filter { !it.isBreak && (it.scheduledToday || it.habit.frequency == HabitFrequency.WEEKLY) }
    val breakHabits = habits.orEmpty().filter { it.isBreak }
    val doneCount = due.count { it.satisfied }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                    Text(Encouragement.general(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings") }
            }
        }
        item { HeroCard(profile, onClick = { nav.navigate("hero") }) }

        if (!shieldOn) item {
            Card(
                onClick = { nav.navigate("shield") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WarningAmber, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.shield_is_off), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.turn_it_on_to_block_distracting), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { FocusCard(focus?.endsAt, focus?.label, onOpen = { nav.navigate("focus") }) }
        item { TodayQuestCard(questBoard, settings.gentleMode, profile?.displayName.orEmpty(), onOpen = { nav.navigate("quests") }) }

        item {
            SectionHeader(stringResource(R.string.today_s_habits, doneCount, due.size)) {
                TextButton(onClick = { nav.navigate("habit_edit/new") }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text(stringResource(R.string.add))
                }
            }
        }
        if (habits != null && due.isEmpty() && breakHabits.isEmpty()) item {
            EmptyState(
                Icons.Filled.ShieldMoon, stringResource(R.string.no_habits_yet),
                "Add a habit or two — small and specific works best. \"Read 10 pages\" beats \"read more\".",
                scene = SpriteCatalog.EMPTY_TODAY,
            )
        }
        items(due, key = { it.habit.id }) { s ->
            HabitRow(s, onTap = { vm.tap(s.habit.id) }, onUndo = { vm.undo(s.habit.id) }, onOpen = { nav.navigate("habit/${s.habit.id}") })
        }
        if (breakHabits.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.breaking_free)) }
            items(breakHabits, key = { "b" + it.habit.id }) { s ->
                HabitRow(s, onTap = { vm.tap(s.habit.id) }, onUndo = { vm.undo(s.habit.id) }, onOpen = { nav.navigate("habit/${s.habit.id}") })
            }
        }

        val active = goals.filter { it.goal.status == GoalStatus.ACTIVE }.take(3)
        if (active.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.your_ascents)) { TextButton(onClick = { nav.navigate("goals") }) { Text(stringResource(R.string.all)) } } }
            items(active, key = { "g" + it.goal.id }) { g ->
                Card(onClick = { nav.navigate("goal/${g.goal.id}") }, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(g.goal.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { g.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = paletteColor(g.goal.colorIndex),
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.camps_reached, g.done, g.milestones.size), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusCard(endsAt: Long?, label: String?, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) { now = System.currentTimeMillis(); delay(1000) }
    }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, null, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (endsAt != null) {
                    val left = ((endsAt - now) / 1000).coerceAtLeast(0)
                    Text("Focusing${if (!label.isNullOrBlank()) ": $label" else ""}", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.time_remaining, left / 60, left % 60), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.ready_to_focus), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.earn_xp_for_every_distraction_free), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (endsAt == null) FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.start)) }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Up late?"
}
