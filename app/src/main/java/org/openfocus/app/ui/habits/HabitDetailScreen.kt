package org.openfocus.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.openfocus.app.core.Days
import org.openfocus.app.core.GameRules
import org.openfocus.app.data.db.HabitFrequency
import org.openfocus.app.data.db.HabitKind
import org.openfocus.app.ui.components.Heatmap
import org.openfocus.app.ui.components.SectionHeader
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.theme.paletteColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(nav: NavController, id: String) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val flow = remember(id) { c.habits.observeState(id) }
    val state by flow.collectAsState(initial = null)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state?.habit?.title ?: "") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { nav.navigate("habit_edit/$id") }) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; scope.launch { c.habits.archive(id); nav.popBackStack() } })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                }
            },
        )
        val s = state ?: return@Column
        val h = s.habit
        val color = paletteColor(h.colorIndex)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (h.notes.isNotBlank()) Text(h.notes, style = MaterialTheme.typography.bodyLarge)
            val total = s.counts.values.sum()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(if (h.kind == HabitKind.BREAK) "Clean days" else if (h.frequency == HabitFrequency.WEEKLY) "Week streak" else "Streak", "${s.streak}", Modifier.weight(1f))
                Stat(if (h.kind == HabitKind.BREAK) "Slips" else "Completions", "$total", Modifier.weight(1f))
                Stat("Next reward", if (h.kind == HabitKind.BREAK) "—" else "${GameRules.habitXp(h.difficulty, s.streak + 1)} XP", Modifier.weight(1f))
            }
            SectionHeader("Last 18 weeks")
            Heatmap(s.counts, h.target, if (h.kind == HabitKind.BREAK) MaterialTheme.colorScheme.error else color, Days.today())
            Text(
                when (h.frequency) {
                    HabitFrequency.DAILY -> "Every day" + if (h.target > 1) ", ${h.target}× a day" else ""
                    HabitFrequency.WEEKLY -> "${h.target}× per week"
                    HabitFrequency.SPECIFIC_DAYS -> Days.shortNames.filterIndexed { i, _ -> Days.maskHas(h.daysMask, i) }.joinToString(", ")
                } + " · ${h.difficulty.label}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete habit?") },
            text = { Text("History for this habit will no longer be shown. XP you've earned stays yours.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { c.habits.delete(id); nav.popBackStack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
