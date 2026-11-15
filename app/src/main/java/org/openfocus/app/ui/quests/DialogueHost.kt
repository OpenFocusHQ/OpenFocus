package org.openfocus.app.ui.quests

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.openfocus.app.R
import org.openfocus.app.core.content.DialogueLine
import org.openfocus.app.core.content.GameContent
import org.openfocus.app.data.db.ProfileEntity
import org.openfocus.app.data.repo.Conversation
import org.openfocus.app.ui.components.appContainer

/** Shows quest conversations (stage intros, boss taunts, NPC chatter) one line at a time. */
@Composable
fun DialogueHost() {
    val c = appContainer()
    val queue = remember { mutableStateListOf<Conversation>() }
    var index by remember { mutableIntStateOf(0) }
    val profile by remember { c.db.profileDao().observe() }.collectAsState(initial = null)
    LaunchedEffect(Unit) { c.quests.conversations.collect { if (it.lines.isNotEmpty()) queue.add(it) } }

    val convo = queue.firstOrNull() ?: return
    val line = convo.lines.getOrNull(index) ?: return
    fun advance() {
        if (index + 1 < convo.lines.size) index++ else { queue.removeAt(0); index = 0 }
    }
    Dialog(onDismissRequest = { queue.removeAt(0); index = 0 }) {
        DialogueCard(convo.title, line, profile, onTap = ::advance)
    }
}

@Composable
fun DialogueCard(title: String, line: DialogueLine, profile: ProfileEntity?, onTap: () -> Unit) {
    val content = GameContent.current
    val npc = content.npc(line.speaker)
    val enemy = content.enemy(line.speaker)
    val name = npc?.name ?: enemy?.name ?: profile?.displayName ?: stringResource(R.string.dialogue_you)
    Card(
        Modifier.fillMaxWidth().clickable(MutableInteractionSource(), null, onClick = onTap),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title.isNotBlank()) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedContent(line, label = stringResource(R.string.line)) { l ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.heightIn(min = 96.dp)) {
                    when {
                        enemy != null -> EnemyPortrait(enemy, 80.dp)
                        npc != null -> NpcPortrait(npc, 72.dp)
                        else -> HeroPortrait(profile, 72.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(l.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text(
                stringResource(R.string.dialogue_continue),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
