package org.openfocus.app.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openfocus.app.core.Encouragement
import org.openfocus.app.game.GameFeedback
import org.openfocus.app.ui.components.AppIcons
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.theme.Coin

/** Shows game feedback: small wins as snackbars, big moments as a celebration dialog. */
@Composable
fun FeedbackHost(snackbar: SnackbarHostState) {
    val game = appContainer().game
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
                snackbar.showSnackbar(fb.message + if (parts.isNotEmpty()) "  (${parts.joinToString(" · ")})" else "")
            }
        }
    }
    SnackbarHost(snackbar)
    big?.let { fb ->
        AlertDialog(
            onDismissRequest = { big = null },
            icon = { Icon(if (fb.fainted) Icons.Filled.Hotel else Icons.Filled.Celebration, null, modifier = Modifier.size(40.dp)) },
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
                    if (fb.fainted) Text("Your HP ran out, so your hero took a rest and lost a few coins. ${Encouragement.comeback()}")
                    if (fb.newLevel != null) Text("You're getting stronger. HP fully restored!")
                    fb.achievements.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.get(a.icon), null, tint = Coin, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(a.title, style = MaterialTheme.typography.titleMedium)
                                Text(a.description, style = MaterialTheme.typography.bodySmall)
                                Text("+${a.xpReward} XP · +${a.coinReward} coins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (!fb.fainted && fb.message.isNotBlank() && fb.achievements.isEmpty()) Text(fb.message)
                }
            },
            confirmButton = { TextButton(onClick = { big = null }) { Text("Onwards!") } },
        )
    }
}
