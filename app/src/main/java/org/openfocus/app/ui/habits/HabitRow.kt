package org.openfocus.app.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openfocus.app.data.db.HabitFrequency
import org.openfocus.app.data.repo.HabitState
import org.openfocus.app.ui.components.AppIcons
import org.openfocus.app.ui.theme.paletteColor

@Composable
fun HabitRow(state: HabitState, onTap: () -> Unit, onUndo: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val h = state.habit
    val color = paletteColor(h.colorIndex)
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.get(h.icon), null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(h.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.isBreak) {
                if (state.todayCount > 0) {
                    IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo slip") }
                }
                OutlinedButton(onClick = onTap) { Text(if (state.todayCount > 0) "Slipped ×${state.todayCount}" else "I slipped") }
            } else {
                if (state.todayCount > 0 && (h.target > 1 || h.frequency == HabitFrequency.WEEKLY)) {
                    IconButton(onClick = onUndo) { Icon(Icons.Filled.Remove, "Undo") }
                }
                val bg by animateColorAsState(if (state.satisfied) color else color.copy(alpha = 0.12f), label = "check")
                FilledIconButton(
                    onClick = { if (state.satisfied && h.target <= 1 && h.frequency != HabitFrequency.WEEKLY) onUndo() else onTap() },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = bg, contentColor = if (state.satisfied) Color.White else color),
                    modifier = Modifier.size(44.dp),
                ) {
                    if (state.satisfied || h.target <= 1) Icon(Icons.Filled.Check, "Complete")
                    else Text("${state.periodProgress}/${h.target}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun subtitle(s: HabitState): String {
    val h = s.habit
    val streak = s.streak
    return when {
        s.isBreak -> if (streak > 0) "🌱 $streak clean day${if (streak > 1) "s" else ""}" else "Fresh start today"
        h.frequency == HabitFrequency.WEEKLY ->
            "${s.periodProgress}/${h.target} this week" + if (streak > 0) "  ·  🔥 $streak wk" else ""
        else -> buildString {
            append(h.difficulty.label)
            if (h.target > 1) append("  ·  ${s.periodProgress}/${h.target} today")
            if (streak > 0) append("  ·  🔥 $streak")
        }
    }
}
