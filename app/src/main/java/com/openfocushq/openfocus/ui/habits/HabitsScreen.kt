package com.openfocushq.openfocus.ui.habits

import com.openfocushq.openfocus.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.openfocushq.openfocus.data.db.HabitFrequency
import com.openfocushq.openfocus.ui.components.EmptyState
import com.openfocushq.openfocus.ui.pixel.SpriteCatalog
import com.openfocushq.openfocus.ui.components.SectionHeader
import com.openfocushq.openfocus.ui.components.appViewModel

@Composable
fun HabitsScreen(nav: NavController) {
    val vm = appViewModel { HabitsViewModel(it) }
    val habits by vm.habits.collectAsState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringResource(R.string.habits), style = MaterialTheme.typography.headlineMedium) }
            val list = habits ?: return@LazyColumn
            if (list.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.CheckCircle, stringResource(R.string.no_habits_yet),
                        stringResource(R.string.habits_intro),
                        scene = SpriteCatalog.EMPTY_HABITS)
                }
            }
            val groups = listOf(
                "Due today" to list.filter { !it.isBreak && it.scheduledToday && it.habit.frequency != HabitFrequency.WEEKLY },
                "This week" to list.filter { !it.isBreak && it.habit.frequency == HabitFrequency.WEEKLY },
                "Not scheduled today" to list.filter { !it.isBreak && !it.scheduledToday },
                "Breaking free" to list.filter { it.isBreak },
            )
            groups.filter { it.second.isNotEmpty() }.forEach { (title, items) ->
                item(key = "h_$title") { SectionHeader(title) }
                items(items, key = { it.habit.id }) { s ->
                    HabitRow(s, onTap = { vm.tap(s.habit.id) }, onUndo = { vm.undo(s.habit.id) }, onOpen = { nav.navigate("habit/${s.habit.id}") })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.navigate("habit_edit/new") },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.new_habit)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}
