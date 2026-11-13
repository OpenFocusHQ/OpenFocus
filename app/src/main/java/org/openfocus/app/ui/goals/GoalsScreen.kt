package org.openfocus.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.openfocus.app.core.Days
import org.openfocus.app.data.db.GoalStatus
import org.openfocus.app.data.repo.GoalState
import org.openfocus.app.ui.components.AscentMountain
import org.openfocus.app.ui.components.EmptyState
import org.openfocus.app.ui.pixel.SpriteCatalog
import org.openfocus.app.ui.components.Pill
import org.openfocus.app.ui.components.SectionHeader
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.theme.Success
import org.openfocus.app.ui.theme.paletteColor

@Composable
fun GoalsScreen(nav: NavController) {
    val c = appContainer()
    val goals by remember { c.goals.observeGoals() }.collectAsState(initial = null)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Long-term goals", style = MaterialTheme.typography.headlineMedium)
                Text("Each goal is a mountain. Milestones are camps on the way up.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val list = goals ?: return@LazyColumn
            if (list.isEmpty()) item {
                EmptyState(Icons.Filled.Landscape, "Pick a summit",
                    "What do you want to have achieved in 3, 6 or 12 months? Break it into milestones and link habits to it.",
                    scene = SpriteCatalog.EMPTY_GOALS)
            }
            val sections = listOf(
                "Climbing" to list.filter { it.goal.status == GoalStatus.ACTIVE },
                "Paused" to list.filter { it.goal.status == GoalStatus.PAUSED },
                "Summits reached" to list.filter { it.goal.status == GoalStatus.COMPLETED },
            )
            sections.filter { it.second.isNotEmpty() }.forEach { (title, gs) ->
                item(key = "s$title") { SectionHeader(title) }
                items(gs, key = { it.goal.id }) { g -> GoalCard(g) { nav.navigate("goal/${g.goal.id}") } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.navigate("goal_edit/new") },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New goal") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun GoalCard(g: GoalState, onClick: () -> Unit) {
    val color = paletteColor(g.goal.colorIndex)
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AscentMountain(
                g.milestones.map { it.done }, g.goal.status == GoalStatus.COMPLETED, g.goal.colorIndex,
                Modifier.width(108.dp).height(76.dp).clip(RoundedCornerShape(12.dp)), animate = false,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.goal.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("${(g.progress * 100).toInt()}% · ${g.done}/${g.milestones.size} camps", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                g.goal.targetEpochDay?.let { t ->
                    val left = t - Days.today()
                    when {
                        g.goal.status == GoalStatus.COMPLETED -> Pill("Summited", Success)
                        left >= 0 -> Pill("$left days left", color)
                        else -> Pill("Past target date", MaterialTheme.colorScheme.error)
                    }
                } ?: if (g.goal.status == GoalStatus.COMPLETED) Pill("Summited", Success) else Unit
            }
        }
    }
}
