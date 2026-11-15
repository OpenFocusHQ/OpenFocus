package org.openfocus.app.ui.habits

import org.openfocus.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.openfocus.app.core.Days
import org.openfocus.app.data.db.Difficulty
import org.openfocus.app.data.db.HabitEntity
import org.openfocus.app.data.db.HabitFrequency
import org.openfocus.app.data.db.HabitKind
import org.openfocus.app.data.db.newId
import org.openfocus.app.ui.components.AppIcons
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.theme.PaletteColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorScreen(nav: NavController, habitId: String?) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val goals by c.goals.observeGoals().collectAsState(initial = emptyList())

    var loaded by remember { mutableStateOf(habitId == null) }
    var original by remember { mutableStateOf<HabitEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(HabitKind.BUILD) }
    var freq by remember { mutableStateOf(HabitFrequency.DAILY) }
    var target by remember { mutableIntStateOf(1) }
    var days by remember { mutableIntStateOf(0b1111111) }
    var difficulty by remember { mutableStateOf(Difficulty.EASY) }
    var icon by remember { mutableStateOf("Star") }
    var color by remember { mutableIntStateOf(0) }
    var goalId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(habitId) {
        if (habitId != null) {
            c.habits.get(habitId)?.let { h ->
                original = h; title = h.title; notes = h.notes; kind = h.kind; freq = h.frequency
                target = h.target; days = h.daysMask; difficulty = h.difficulty; icon = h.icon
                color = h.colorIndex; goalId = h.goalId
            }
            loaded = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (habitId == null) "New habit" else "Edit habit") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        if (!loaded) return@Column
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(kind == HabitKind.BUILD, { kind = HabitKind.BUILD }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.build_a_habit)) }
                SegmentedButton(kind == HabitKind.BREAK, { kind = HabitKind.BREAK }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.break_a_habit)) }
            }
            OutlinedTextField(
                title, { title = it }, Modifier.fillMaxWidth(),
                label = { Text(if (kind == HabitKind.BUILD) "e.g. Read 10 pages" else "e.g. Doomscrolling in bed") },
                singleLine = true,
            )
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.notes_optional)) })

            if (kind == HabitKind.BUILD) {
                Text(stringResource(R.string.how_often), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HabitFrequency.entries.forEachIndexed { i, f ->
                        SegmentedButton(freq == f, { freq = f; if (f == HabitFrequency.WEEKLY && target < 2) target = 3 }, SegmentedButtonDefaults.itemShape(i, 3)) {
                            Text(when (f) { HabitFrequency.DAILY -> "Daily"; HabitFrequency.SPECIFIC_DAYS -> "Some days"; HabitFrequency.WEEKLY -> "Per week" })
                        }
                    }
                }
                if (freq == HabitFrequency.SPECIFIC_DAYS) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Days.shortNames.forEachIndexed { i, n ->
                            FilterChip(selected = Days.maskHas(days, i), onClick = { days = days xor (1 shl i) }, label = { Text(n) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (freq == HabitFrequency.WEEKLY) "Times per week" else "Times per day", Modifier.weight(1f))
                    IconButton(onClick = { if (target > 1) target-- }) { Icon(Icons.Filled.Remove, "Less") }
                    Text("$target", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { if (target < 20) target++ }) { Icon(Icons.Filled.Add, "More") }
                }
            }

            Text(stringResource(R.string.difficulty_bigger_rewards_for_harder_habits), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Difficulty.entries.forEach { d -> FilterChip(selected = difficulty == d, onClick = { difficulty = d }, label = { Text(d.label) }) }
            }

            Text(stringResource(R.string.icon), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcons.habitChoices.forEach { (name, vec) ->
                    val sel = name == icon
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (sel) PaletteColors[color] else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { icon = name },
                        contentAlignment = Alignment.Center,
                    ) { Icon(vec, name, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Text(stringResource(R.string.color), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaletteColors.forEachIndexed { i, col ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(col)
                            .then(if (i == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = i },
                    )
                }
            }

            val activeGoals = goals.map { it.goal }
            if (activeGoals.isNotEmpty()) {
                Text(stringResource(R.string.supports_a_long_term_goal), style = MaterialTheme.typography.titleSmall)
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }) {
                        Text(activeGoals.firstOrNull { it.id == goalId }?.title ?: "None")
                    }
                    DropdownMenu(open, { open = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.none)) }, onClick = { goalId = null; open = false })
                        activeGoals.forEach { g -> DropdownMenuItem(text = { Text(g.title) }, onClick = { goalId = g.id; open = false }) }
                    }
                }
            }

            Button(
                enabled = title.isNotBlank() && (freq != HabitFrequency.SPECIFIC_DAYS || days != 0),
                onClick = {
                    scope.launch {
                        val base = original ?: HabitEntity(id = newId(), ownerId = "", title = "")
                        c.habits.save(
                            base.copy(
                                title = title.trim(), notes = notes.trim(), kind = kind,
                                frequency = if (kind == HabitKind.BREAK) HabitFrequency.DAILY else freq,
                                target = if (kind == HabitKind.BREAK) 1 else target, daysMask = days,
                                difficulty = difficulty, icon = icon, colorIndex = color, goalId = goalId,
                            ),
                        )
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
