package org.openfocus.app.ui.nav

import org.openfocus.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openfocus.app.core.Encouragement
import org.openfocus.app.game.GameFeedback
import org.openfocus.app.ui.components.AchievementBadge
import org.openfocus.app.ui.components.HeroAvatar
import org.openfocus.app.ui.pixel.HeroPose
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.pixel.PixelSprite
import org.openfocus.app.ui.pixel.SpriteCatalog

/** Snackbar payload that knows whether coins were earned (shows the spinning pixel coin). */
private data class RewardSnackbar(
    override val message: String,
    val coins: Int,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

/** Shows game feedback: small wins as snackbars, big moments as a celebration dialog. */
@Composable
fun FeedbackHost(snackbar: SnackbarHostState) {
    val container = appContainer()
    val game = container.game
    val profile = remember { container.db.profileDao().observe() }
    var big by remember { mutableStateOf<GameFeedback?>(null) }
    LaunchedEffect(Unit) {
        game.feedback.collect { fb ->
            if (fb.newLevel != null || fb.achievements.isNotEmpty() || fb.fainted) {
                big = fb
            } else if (fb.message.isNotBlank()) {
                val parts = buildList {
                    if (fb.xp > 0) add("+${fb.xp} XP")
                    if (fb.coins > 0) add("+${fb.coins} coins")
                    if (fb.hp < 0) add("${fb.hp} HP")
                }
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(
                    RewardSnackbar(fb.message + if (parts.isNotEmpty()) "  (${parts.joinToString(" · ")})" else "", fb.coins),
                )
            }
        }
    }
    SnackbarHost(snackbar) { data ->
        Snackbar {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (((data.visuals as? RewardSnackbar)?.coins ?: 0) > 0) {
                    PixelSprite(SpriteCatalog.coin, "spin", Modifier.size(24.dp), contentDescription = stringResource(R.string.coins_earned))
                    Spacer(Modifier.width(10.dp))
                }
                Text(data.visuals.message)
            }
        }
    }
    big?.let { fb ->
        AlertDialog(
            onDismissRequest = { big = null },
            icon = {
                val p by profile.collectAsState(initial = null)
                Box(contentAlignment = Alignment.Center) {
                    HeroAvatar(p, size = 120.dp, pose = if (fb.fainted) HeroPose.REST else HeroPose.CELEBRATE)
                    // Same 48px logical size as the avatar, so both snap to the same pixel scale.
                    if (!fb.fainted) PixelSprite(SpriteCatalog.sparkle, SpriteCatalog.IDLE, Modifier.size(120.dp))
                }
            },
            title = {
                Text(
                    when {
                        fb.fainted -> "You needed a rest"
                        fb.newLevel != null -> "Level ${fb.newLevel}!"
                        else -> "Achievement unlocked!"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (fb.fainted) Text(stringResource(R.string.your_hp_ran_out_so_your, Encouragement.comeback()))
                    if (fb.newLevel != null) Text(stringResource(R.string.you_re_getting_stronger_hp_fully))
                    fb.achievements.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AchievementBadge(a.id, unlocked = true, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(a.title, style = MaterialTheme.typography.titleMedium)
                                Text(a.description, style = MaterialTheme.typography.bodySmall)
                                Text(listOfNotNull(a.xpReward.takeIf { it > 0 }?.let { "+$it XP" }, a.coinReward.takeIf { it > 0 }?.let { "+$it coins" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (!fb.fainted && fb.message.isNotBlank() && fb.achievements.isEmpty()) Text(fb.message)
                }
            },
            confirmButton = { TextButton(onClick = { big = null }) { Text(stringResource(R.string.onwards)) } },
        )
    }
}
