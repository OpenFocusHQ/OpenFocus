package org.openfocus.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.openfocus.app.core.Days
import org.openfocus.app.core.GameRules
import org.openfocus.app.data.db.GoalStatus
import org.openfocus.app.ui.components.AscentMountain
import org.openfocus.app.ui.components.SectionHeader
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.components.formatMinutes
import org.openfocus.app.ui.theme.paletteColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(nav: NavController, id: String) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val state by remember(id) { c.goals.observeGoal(id) }.collectAsState(initial = null)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newMilestone by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state?.goal?.title ?: "") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { nav.navigate("goal_edit/$id") }) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    val st = state?.goal?.status
                    if (st == GoalStatus.ACTIVE) DropdownMenuItem(text = { Text("Pause") }, onClick = { menu = false; scope.launch { c.goals.setStatus(id, GoalStatus.PAUSED) } })
                    if (st == GoalStatus.PAUSED) DropdownMenuItem(text = { Text("Resume") }, onClick = { menu = false; scope.launch { c.goals.setStatus(id, GoalStatus.ACTIVE) } })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                }
            },
        )
        val s = state ?: return@Column
        val g = s.goal
        val color = paletteColor(g.colorIndex)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AscentMountain(
                s.milestones.map { it.done }, g.status == GoalStatus.COMPLETED, g.colorIndex,
                Modifier.fillMaxWidth().aspectRatio(160f / 112f).clip(RoundedCornerShape(20.dp)),
                integerScale = false,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.summitName.ifBlank { "The summit" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, color = color)
            }
            if (g.why.isNotBlank()) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Why this matters", style = MaterialTheme.typography.labelLarge)
                        Text(g.why, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            g.targetEpochDay?.let { t ->
                val left = t - Days.today()
                Text(
                    if (left >= 0) "Target: ${Days.date(t)} · $left days to go" else "Target date ${Days.date(t)} has passed — adjust it, no shame in that.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text("Effort so far: ${s.habitCompletions} linked habit check-ins · ${formatMinutes(s.focusMinutes)} focused",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader("Camps (milestones)")
            s.milestones.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(m.done, { scope.launch { c.goals.toggleMilestone(m.id) } })
                    Text(m.title, Modifier.weight(1f), textDecoration = if (m.done) TextDecoration.LineThrough else null)
                    IconButton(onClick = { scope.launch { c.goals.deleteMilestone(m) } }) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newMilestone, { newMilestone = it }, Modifier.weight(1f), label = { Text("Add a milestone") }, singleLine = true)
                IconButton(onClick = {
                    val t = newMilestone; newMilestone = ""
                    scope.launch { c.goals.addMilestone(id, t, s.milestones.size) }
                }) { Icon(Icons.Filled.Add, "Add") }
            }
            Text("+${GameRules.MILESTONE_XP} XP per camp · +${GameRules.GOAL_XP} XP at the summit", style = MaterialTheme.typography.labelSmall)
            if (g.status != GoalStatus.COMPLETED) {
                Button(onClick = { scope.launch { c.goals.complete(id) } }, modifier = Modifier.fillMaxWidth()) { Text("I reached the summit!") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete goal?") },
            text = { Text("Milestones will be removed. Linked habits stay.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { c.goals.delete(id); nav.popBackStack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
