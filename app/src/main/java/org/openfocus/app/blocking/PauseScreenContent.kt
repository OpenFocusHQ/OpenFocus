package org.openfocus.app.blocking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openfocus.app.core.GameRules
import org.openfocus.app.data.db.BlockGate
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
    val canPay: Boolean = true,
    val backToApp: Boolean = false,
    val focusEndsAt: Long? = null,
    /** User's own question; `{app}` becomes the app name. Blank = [DEFAULT_QUESTION]. */
    val question: String = "",
    /** User's personal reminder. Replaces the context line when set. */
    val reminder: String = "",
) {
    val waiting: Boolean get() = gate == BlockGate.WAIT && elapsedMs < waitMs
    val progress: Float get() = if (waitMs <= 0) 1f else (elapsedMs.toFloat() / waitMs).coerceIn(0f, 1f)
    val secondsLeft: Int get() = ((waitMs - elapsedMs + 999) / 1000).toInt().coerceAtLeast(0)

    /** Unlock lengths offered for free once the wait is over. */
    val freeDurations: List<Int> get() = GameRules.freeDurations(allowMinutes)

    /** The gift (coins) button: only while there's something to pay for. */
    val showCoins: Boolean get() = canPay && reason != BlockReason.SHORTS && (gate == BlockGate.HARD || waiting)

    val headline: String
        get() = if (reason == BlockReason.SHORTS) "$detail blocked"
        else question.ifBlank { DEFAULT_QUESTION }.replace("{app}", appLabel)

    /** At most one quiet line under the question (Ascent-style: say little). */
    fun subline(now: Long = System.currentTimeMillis()): String? = when {
        reason == BlockReason.SHORTS -> if (backToApp) "The rest of $appLabel still works." else "$appLabel is all short videos."
        reminder.isNotBlank() -> reminder
        else -> contextLine(now)
    }

    /** Why it's blocked, when that's worth saying (null for a plain always-on block). */
    fun contextLine(now: Long = System.currentTimeMillis()): String? = when (reason) {
        BlockReason.SHORTS -> null
        BlockReason.FOCUS, BlockReason.DEEP_FOCUS -> {
            val left = focusEndsAt?.let { ((it - now) / 60_000).toInt() + 1 }
            "You're in a focus session" + (if (detail.isNotBlank()) " on \"$detail\"" else "") +
                (left?.let { " · about $it min left." } ?: ".")
        }
        BlockReason.DAILY_LIMIT -> "You've used today's $detail for this app."
        BlockReason.SCHEDULE -> "Scheduled rest: $detail."
        BlockReason.ALWAYS -> null
    }

    /** Text under the buttons when there's no way through (hard blocks). */
    val hardNote: String?
        get() = when {
            gate != BlockGate.HARD || reason == BlockReason.SHORTS -> null
            !canPay -> "Strict mode: no way through until your focus session ends."
            else -> null
        }
}

const val DEFAULT_QUESTION = "Do you really need {app} right now?"

/** One row in the unlock sheet. [key] identifies the choice ("free:5", "paid:15", "reward:<id>"). */
data class UnlockOption(
    val key: String,
    val label: String,
    val price: String,
    val enabled: Boolean = true,
    /** Rows with a different section than the previous row get a small header. */
    val section: String? = null,
)

/** The bottom sheet used both for "how long?" after waiting and for coin unlocks. */
data class UnlockSheet(val title: String, val subtitle: String? = null, val options: List<UnlockOption>, val footer: String? = null)

/**
 * Ascent-style pause screen: one icon, one question, at most one quiet line, then "Close app"
 * as the primary action and (for wait gates) a "Continue in app" button that fills up while the
 * wait runs down. Choosing *how long* happens in a sheet after tapping Continue; coins live
 * behind the gift button.
 */
@Composable
fun PauseScreenContent(
    s: PauseState,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onBackToApp: () -> Unit,
    onOpenCoins: () -> Unit,
    sheet: UnlockSheet? = null,
    onPick: (String) -> Unit = {},
    onDismissSheet: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (s.showCoins) {
                    IconButton(onClick = onOpenCoins, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Filled.Redeem, contentDescription = "Use coins")
                    }
                }

                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PixelSprite(SpriteCatalog.shield, SpriteCatalog.IDLE, Modifier.size(96.dp), contentDescription = "Shield")
                    Text(s.headline, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    s.subline()?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shortsWithApp = s.reason == BlockReason.SHORTS && s.backToApp
                    PrimaryButton(if (shortsWithApp) "Back to ${s.appLabel}" else "Close app", if (shortsWithApp) onBackToApp else onClose)
                    when {
                        shortsWithApp -> TextButton(onClick = onClose) { Text("Close ${s.appLabel}") }
                        s.gate == BlockGate.WAIT && s.reason != BlockReason.SHORTS -> ContinueButton(s, onContinue)
                        else -> s.hardNote?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            if (sheet != null) UnlockSheetOverlay(sheet, onPick, onDismissSheet)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
        ),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/** "Continue in app": fills from left to right while the wait runs down; tap it when full. */
@Composable
private fun ContinueButton(s: PauseState, onClick: () -> Unit) {
    val ready = !s.waiting
    val shape = RoundedCornerShape(20.dp)
    val label = if (ready) "Continue in app" else "Continue in app, available in ${s.secondsLeft} seconds"
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
            if (!ready) Text("${s.secondsLeft}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = onColor)
        }
    }
}

/** Bottom sheet drawn inside the pause screen (no extra window): one tap picks an option. */
@Composable
fun UnlockSheetOverlay(sheet: UnlockSheet, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 640.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant))
                Text(sheet.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                sheet.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(4.dp))
                var lastSection: String? = null
                sheet.options.forEach { o ->
                    if (o.section != lastSection && o.section != null) {
                        Text(o.section, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    }
                    lastSection = o.section
                    OptionRow(o) { onPick(o.key) }
                }
                sheet.footer?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun OptionRow(o: UnlockOption, onClick: () -> Unit) {
    val alpha = if (o.enabled) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
            .clickable(enabled = o.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(o.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        Text(o.price, style = MaterialTheme.typography.labelLarge,
            color = (if (o.price == "Free") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = alpha))
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

