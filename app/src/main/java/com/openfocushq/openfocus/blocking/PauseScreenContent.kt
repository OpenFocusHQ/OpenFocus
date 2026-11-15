package com.openfocushq.openfocus.blocking

import com.openfocushq.openfocus.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import com.openfocushq.openfocus.ui.theme.Body
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
import com.openfocushq.openfocus.core.GameRules
import com.openfocushq.openfocus.data.db.BlockGate
import com.openfocushq.openfocus.ui.pixel.PixelSprite
import com.openfocushq.openfocus.ui.pixel.SpriteCatalog

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
    val icon: PauseIcon = PauseIcon.SHIELD,
    val colors: PauseColors = PauseColors.SYSTEM,
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

/** Resolved pause-screen colours: [PauseColors.SYSTEM] follows the phone's dark mode with neutral greys. */
@Composable
fun pauseColors(c: PauseColors): Pair<Color, Color> {
    if (c != PauseColors.SYSTEM) return Color(c.background) to Color(c.foreground)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color(PauseColors.DARK_BG) to Color(PauseColors.DARK_FG) else Color(PauseColors.LIGHT_BG) to Color(PauseColors.LIGHT_FG)
}

/** The pause screen's icon: the pixel shield, or an outline icon tinted with the foreground colour. */
@Composable
fun PauseIconView(icon: PauseIcon, tint: Color, modifier: Modifier = Modifier) {
    if (icon.vector == null) PixelSprite(SpriteCatalog.shield, SpriteCatalog.IDLE, modifier, contentDescription = stringResource(R.string.shield))
    else Icon(icon.vector, contentDescription = icon.label, tint = tint, modifier = modifier)
}

/**
 * Ascent-style pause screen: one icon, one question in a calm regular weight, at most one quiet
 * line, then "Close app" and (for wait gates) a "Continue in app" bar that drains while the wait
 * runs down. Choosing how long happens in a card after tapping Continue; coins sit behind the gift.
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
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = pauseColors(s.colors)
    Surface(modifier.fillMaxSize(), color = bg, contentColor = fg) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (s.showCoins) {
                    IconButton(onClick = onOpenCoins, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Filled.Redeem, contentDescription = stringResource(R.string.use_coins), tint = fg)
                    }
                }

                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PauseIconView(s.icon, fg, Modifier.size(88.dp))
                    Text(
                        s.headline, textAlign = TextAlign.Center, color = fg,
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = Body, fontWeight = FontWeight.Normal, lineHeight = 44.sp),
                    )
                    s.subline()?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = fg.copy(alpha = 0.7f))
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shortsWithApp = s.reason == BlockReason.SHORTS && s.backToApp
                    PrimaryButton(if (shortsWithApp) "Back to ${s.appLabel}" else "Close app", fg, bg, if (shortsWithApp) onBackToApp else onClose)
                    when {
                        shortsWithApp -> QuietButton("Close ${s.appLabel}", fg, onClose)
                        s.gate == BlockGate.WAIT && s.reason != BlockReason.SHORTS -> ContinueButton(s, fg, onContinue)
                        else -> s.hardNote?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            AnimatedVisibility(sheet != null, enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.96f), exit = fadeOut(tween(150))) {
                // Keep showing the last sheet while it fades out.
                val last = remember { mutableStateOf(sheet) }
                if (sheet != null) last.value = sheet
                last.value?.let { UnlockCard(it, onPick, onDismissSheet) }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, fg: Color, bg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
    ) { Text(text, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Medium)) }
}

@Composable
private fun QuietButton(text: String, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Medium), color = fg) }
}

/**
 * "Continue in app": a grey bar that drains from left to right as the wait runs down, with the
 * seconds left underneath. When it's empty the button turns into plain text you can tap.
 */
@Composable
private fun ContinueButton(s: PauseState, fg: Color, onClick: () -> Unit) {
    val ready = !s.waiting
    val label = if (ready) "Continue in app" else "Continue in app, available in ${s.secondsLeft} seconds"
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = ready, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        if (!ready) {
            // Remaining time as a bar anchored to the right edge: it shrinks towards the right.
            Box(Modifier.fillMaxHeight().fillMaxWidth(1f - s.progress).align(Alignment.CenterEnd).background(fg.copy(alpha = 0.18f)))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.continue_in_app), style = MaterialTheme.typography.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Medium), color = fg)
            if (!ready) Text("${s.secondsLeft}", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Body), color = fg)
        }
    }
}

/** Centred card over a scrim (drawn inside the pause screen, no extra window): one tap picks an option. */
@Composable
fun UnlockCard(sheet: UnlockSheet, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Surface(
            Modifier.align(Alignment.Center).padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = 600.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 6.dp,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sheet.title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Medium))
                        sheet.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                }
                HorizontalDivider()
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    var lastSection: String? = null
                    sheet.options.forEach { o ->
                        if (o.section != lastSection && o.section != null) {
                            Text(o.section, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                        }
                        lastSection = o.section
                        OptionRow(o) { onPick(o.key) }
                    }
                    sheet.footer?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(o: UnlockOption, onClick: () -> Unit) {
    val alpha = if (o.enabled) 1f else 0.38f
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(enabled = o.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(o.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        if (o.price.isEmpty()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        } else {
            Text(o.price, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
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

fun formatDuration(minutes: Int): String = when {
    minutes == 60 -> "1 hour"
    minutes > 60 && minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "$minutes min"
}

