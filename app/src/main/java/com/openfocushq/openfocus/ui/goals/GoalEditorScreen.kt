package com.openfocushq.openfocus.ui.goals

import com.openfocushq.openfocus.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.openfocushq.openfocus.core.Days
import com.openfocushq.openfocus.data.db.GoalEntity
import com.openfocushq.openfocus.data.db.newId
import com.openfocushq.openfocus.ui.components.appContainer
import com.openfocushq.openfocus.ui.theme.PaletteColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorScreen(nav: NavController, goalId: String?) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<GoalEntity?>(null) }
    var loaded by remember { mutableStateOf(goalId == null) }
    var title by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var summit by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Long?>(null) }
    var color by remember { mutableIntStateOf(0) }
    var milestones by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }

    LaunchedEffect(goalId) {
        if (goalId != null) {
            c.db.goalDao().get(goalId)?.let { g ->
                original = g; title = g.title; why = g.why; summit = g.summitName; target = g.targetEpochDay; color = g.colorIndex
            }
            loaded = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (goalId == null) "New goal" else "Edit goal") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        if (!loaded) return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.goal_e_g_run_a_half)) }, singleLine = true)
            OutlinedTextField(why, { why = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.why_does_it_matter_to_you)) }, minLines = 2)
            OutlinedTextField(summit, { summit = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name_your_summit_optional)) }, singleLine = true)
            OutlinedButton(onClick = { pickDate = true }) { Text(target?.let { "Target: ${Days.date(it)}" } ?: "Set a target date (optional)") }
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
            if (goalId == null) {
                OutlinedTextField(
                    milestones, { milestones = it }, Modifier.fillMaxWidth(), minLines = 4,
                    label = { Text(stringResource(R.string.milestones_one_per_line)) },
                    placeholder = { Text(stringResource(R.string.run_5_km_without_stopping_run)) },
                )
            }
            Button(
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        val base = original ?: GoalEntity(id = newId(), ownerId = "", title = "")
                        c.goals.save(
                            base.copy(title = title.trim(), why = why.trim(), summitName = summit.trim(), targetEpochDay = target, colorIndex = color),
                            if (goalId == null) milestones.lines() else emptyList(),
                        )
                        nav.popBackStack()
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = target?.let { it * 86_400_000L })
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = { target = state.selectedDateMillis?.let { it / 86_400_000L }; pickDate = false }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { target = null; pickDate = false }) { Text(stringResource(R.string.clear)) } },
        ) { DatePicker(state) }
    }
}
