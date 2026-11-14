package org.openfocus.app.blocking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openfocus.app.core.GameRules
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.data.db.RewardEntity
import org.openfocus.app.ui.pixel.PixelSprite
import org.openfocus.app.ui.pixel.SpriteCatalog

/** Everything the pause screen shows; no timers or I/O, so any moment can be rendered (and tested). */
data class PauseState(
    val appLabel: String,
    val reason: BlockReason,
    val detail: String = "",
    val gate: BlockGate = BlockGate.HARD,
    val waitMs: Long = 0,
    val elapsedMs: Long = 0,
    /** Longest free unlock after waiting (the app's setting). */
    val allowMinutes: Int = 0,
    /** Unlock length the user picked; 0 = the default choice. */
    val selectedMinutes: Int = 0,
    val canPay: Boolean = true,
    val backToApp: Boolean = false,
    val focusEndsAt: Long? = null,
    val nudge: String = "",
    /** User's own question; `{app}` becomes the app name. Blank = [DEFAULT_QUESTION]. */
    val question: String = "",
    /** User's personal reminder ("Remember: you're training for the marathon"). Shown when not blank. */
    val reminder: String = "",
) {
    /** Unlock lengths offered for free after the wait. */
    val freeDurations: List<Int> get() = GameRules.freeDurations(allowMinutes)
    val chosenMinutes: Int get() = selectedMinutes.takeIf { it in freeDurations } ?: GameRules.defaultDuration(freeDurations)

    val headline: String
        get() = if (reason == BlockReason.SHORTS) "$detail blocked"
        else question.ifBlank { DEFAULT_QUESTION }.replace("{app}", appLabel)

    val waiting: Boolean get() = gate == BlockGate.WAIT && elapsedMs < waitMs
    val progress: Float get() = if (waitMs <= 0) 1f else (elapsedMs.toFloat() / waitMs).coerceIn(0f, 1f)
    val secondsLeft: Int get() = ((waitMs - elapsedMs + 999) / 1000).toInt().coerceAtLeast(0)

    /** The gift (coins) button: only while there's something to pay for. */
    val showCoins: Boolean get() = canPay && reason != BlockReason.SHORTS && (gate == BlockGate.HARD || waiting)

    fun contextLine(now: Long = System.currentTimeMillis()): String = when (reason) {
        BlockReason.SHORTS -> "Short-video feeds are built to be endless."
        BlockReason.FOCUS, BlockReason.DEEP_FOCUS -> {
            val left = focusEndsAt?.let { ((it - now) / 60_000).toInt() + 1 }
            "You're in a focus session" + (if (detail.isNotBlank()) " on \"$detail\"" else "") +
                (left?.let { " · about $it min left." } ?: ".")
        }
        BlockReason.DAILY_LIMIT -> "You've used today's $detail for this app."
        BlockReason.SCHEDULE -> "Scheduled rest: $detail."
        BlockReason.ALWAYS -> if (gate == BlockGate.WAIT) "Take a breath before you dive in." else "You chose to block this app."
    }
}

const val DEFAULT_QUESTION = "Do you really need {app} right now?"

/**
 * Ascent-style pause screen: a calm question in the middle, "Close app" as the big primary action,
 * and (for wait gates) a "Continue in app" button that fills up as the wait runs down. Coins live
 * behind the gift button in the corner.
 */
@Composable
fun PauseScreenContent(
    s: PauseState,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onBackToApp: () -> Unit,
    onOpenCoins: () -> Unit,
    onSelectMinutes: (Int) -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (s.showCoins) {
                IconButton(onClick = onOpenCoins, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Filled.Redeem, contentDescription = "Use coins")
                }
            }

            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PixelSprite(SpriteCatalog.shield, SpriteCatalog.IDLE, Modifier.size(112.dp), contentDescription = "Shield")
                Text(s.headline, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(s.contextLine(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (s.reminder.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(s.reminder, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                } else if (s.nudge.isNotBlank()) {
                    Text(s.nudge, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                ) {
                    Text("Close app", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("+${GameRules.RESIST_XP} XP", style = MaterialTheme.typography.labelMedium)
                }
                when {
                    s.reason == BlockReason.SHORTS && s.backToApp ->
                        OutlinedButton(onClick = onBackToApp, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
                            Text("Back to ${s.appLabel} (no Shorts)")
                        }
                    s.gate == BlockGate.WAIT -> {
                        DurationChips("Unlock for", s.freeDurations, s.chosenMinutes, onSelectMinutes)
                        ContinueButton(s, onContinue)
                    }
                    else -> Text(
                        when {
                            s.reason == BlockReason.SHORTS -> "This app is all short videos, so it stays closed."
                            !s.canPay -> "Strict mode: no way through until your focus session ends."
                            else -> "Fully blocked. Tap the gift to spend coins if you truly need it."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** "Continue in app": fills from left to right while the wait runs down, then opens the app. */
@Composable
private fun ContinueButton(s: PauseState, onClick: () -> Unit) {
    val ready = !s.waiting
    val shape = RoundedCornerShape(20.dp)
    val label = if (ready) "Continue in app for ${s.chosenMinutes} minutes" else "Continue in app, available in ${s.secondsLeft} seconds"
    val onColor = if (ready) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = ready, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(s.progress)
                .background(if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant),
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Continue in app", style = MaterialTheme.typography.titleMedium, color = onColor)
            Text(
                if (ready) "for ${s.chosenMinutes} min" else "${s.secondsLeft}",
                style = if (ready) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = onColor,
            )
        }
    }
}

/** A row of unlock lengths to choose from ("1 min", "5 min"…). Shared by the free and the paid flow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationChips(label: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { m ->
                FilterChip(selected = m == selected, onClick = { onSelect(m) }, label = { Text(formatDuration(m)) })
            }
        }
    }
}

fun formatDuration(minutes: Int): String = if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"

/**
 * Pay coins to unlock for a length the user picks: [timeCost] grows with [minutes]; on a wait-timer
 * app, skipping the wait adds [skipCost].
 */
@Composable
fun PaidUnlockDialog(
    appLabel: String,
    coins: Long,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    perMinute: Int,
    timeCost: Int,
    skipWaitSeconds: Int?,
    skipCost: Int,
    escalating: Boolean,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
    /** The user's own rewards that unlock this app; redeemed at their fixed price. */
    rewards: List<RewardEntity> = emptyList(),
    onRedeem: (RewardEntity) -> Unit = {},
) {
    val total = timeCost + skipCost
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PixelSprite(SpriteCatalog.coin, "spin", Modifier.size(32.dp)) },
        title = { Text(if (skipWaitSeconds != null) "Skip the wait" else "Unlock with coins") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DurationChips("Unlock $appLabel for", GameRules.unlockDurations, minutes, onMinutes)
                Text(
                    buildString {
                        append("${formatDuration(minutes)} × $perMinute = $timeCost coins")
                        if (skipWaitSeconds != null) append("\nSkipping the ${GameRules.formatWait(skipWaitSeconds)} wait: +$skipCost coins")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("You have $coins coins." + if (escalating) " Each paid unlock today costs more than the last." else "",
                    style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(enabled = coins >= total, onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock for ${formatDuration(minutes)} · $total coins")
                }
                if (rewards.isNotEmpty()) {
                    Text("Or use one of your rewards", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    rewards.forEach { r ->
                        OutlinedButton(enabled = coins >= r.cost, onClick = { onRedeem(r) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${r.title} · ${formatDuration(r.unlockMinutes)} · ${r.cost} coins", textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Never mind") } },
    )
}
