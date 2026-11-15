package com.openfocushq.openfocus.ui.shield

import com.openfocushq.openfocus.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openfocushq.openfocus.core.GameRules
import com.openfocushq.openfocus.data.db.BlockGate
import kotlin.math.abs
import kotlin.math.roundToInt

/** How the pause screen behaves for an app (or the defaults for new apps). */
data class PauseConfig(val gate: BlockGate, val waitSeconds: Int, val allowMinutes: Int) {
    fun summary(): String =
        if (gate == BlockGate.HARD) "Fully blocked"
        else "Wait ${GameRules.formatWait(waitSeconds)}, then unlock up to $allowMinutes min"
}

/** Gate choice plus, for waits, sliders for the wait length and how long the app stays open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseConfigEditor(config: PauseConfig, onChange: (PauseConfig) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(config.gate == BlockGate.WAIT, { onChange(config.copy(gate = BlockGate.WAIT)) }, SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(stringResource(R.string.wait_timer))
            }
            SegmentedButton(config.gate == BlockGate.HARD, { onChange(config.copy(gate = BlockGate.HARD)) }, SegmentedButtonDefaults.itemShape(1, 2)) {
                Text(stringResource(R.string.fully_blocked))
            }
        }
        if (config.gate == BlockGate.WAIT) {
            StepSlider(
                label = stringResource(R.string.wait_before_continue), value = config.waitSeconds, steps = GameRules.waitStepsSeconds,
                format = GameRules::formatWait, onChange = { onChange(config.copy(waitSeconds = it)) },
            )
            StepSlider(
                label = stringResource(R.string.longest_free_unlock_after_waiting), value = config.allowMinutes, steps = GameRules.allowStepsMinutes,
                format = { "$it min" }, onChange = { onChange(config.copy(allowMinutes = it)) },
            )
        } else {
            Text(stringResource(R.string.only_close_app_is_offered_if),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A slider that snaps to a list of hand-picked values (finer at the short end). */
@Composable
fun StepSlider(label: String, value: Int, steps: List<Int>, format: (Int) -> String, onChange: (Int) -> Unit) {
    val index = steps.indices.minBy { abs(steps[it] - value) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { onChange(steps[it.roundToInt().coerceIn(steps.indices)]) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
        )
    }
}
