package org.openfocus.app.ui.quests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.openfocus.app.R
import org.openfocus.app.core.content.EnemyDef
import org.openfocus.app.core.content.GameContent
import org.openfocus.app.core.content.NpcDef
import org.openfocus.app.core.content.ObjectiveDef
import org.openfocus.app.core.content.ObjectiveMode
import org.openfocus.app.core.content.StageDef
import org.openfocus.app.core.content.StageKind
import org.openfocus.app.data.db.ProfileEntity
import org.openfocus.app.data.repo.ActiveQuest
import org.openfocus.app.data.repo.QuestBoard
import org.openfocus.app.data.repo.QuestEntry
import org.openfocus.app.data.repo.QuestLock
import org.openfocus.app.data.repo.QuestState
import org.openfocus.app.ui.components.EmptyState
import org.openfocus.app.ui.components.SectionHeader
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.pixel.PixelSprite
import org.openfocus.app.ui.pixel.SpriteCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestsScreen(nav: NavController) {
    val c = appContainer()
    val board by remember { c.quests.observeBoard() }.collectAsState(initial = null)
    val profile by remember { c.db.profileDao().observe() }.collectAsState(initial = null)
    val settings by c.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.quests_title)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        QuestsContent(
            board = board,
            coins = (profile?.coins ?: 0L).toInt(),
            gentle = settings.gentleMode,
            onStart = { e ->
                scope.launch {
                    if (!c.quests.accept(e.line.id) && e.line.cost > (profile?.coins ?: 0L)) {
                        c.game.notify(context.getString(R.string.quest_not_enough_coins))
                    }
                }
            },
            onTalk = { id -> scope.launch { c.quests.chat(id) } },
            onLeave = { leaving = true },
        )
    }
    val active = board?.active
    if (leaving && active != null) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text(stringResource(R.string.quest_leave_title)) },
            text = { Text(stringResource(R.string.quest_leave_body, active.line.title)) },
            confirmButton = { TextButton(onClick = { leaving = false; scope.launch { c.quests.abandon() } }) { Text(stringResource(R.string.quest_leave_confirm)) } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Stateless body of the Quests screen (also used by screenshot tests). */
@Composable
fun QuestsContent(
    board: QuestBoard?,
    coins: Int,
    gentle: Boolean,
    onStart: (QuestEntry) -> Unit,
    onTalk: (String) -> Unit,
    onLeave: () -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(stringResource(R.string.quest_current)) }
        item {
            val active = board?.active
            if (active != null) {
                ActiveQuestCard(active, gentle, onTalk = onTalk, onLeave = onLeave)
            } else {
                EmptyState(Icons.Filled.TaskAlt, stringResource(R.string.quest_none_title), stringResource(R.string.quest_none_body))
            }
        }
        val entries = board?.entries.orEmpty()
        val open = entries.filter { it.state != QuestState.ACTIVE && it.state != QuestState.COMPLETED }
        val done = entries.filter { it.state == QuestState.COMPLETED }
        if (open.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.quest_board)) }
            items(open, key = { it.line.id }) { e -> QuestBoardCard(e, coins, onStart = { onStart(e) }) }
        }
        if (done.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.quest_completed)) }
            items(done, key = { "done_" + it.line.id }) { e -> QuestBoardCard(e, coins, onStart = {}) }
        }
    }
}

/** The running quest: who's involved, the stage story and a progress bar (boss HP, finds, objective). */
@Composable
fun ActiveQuestCard(
    q: ActiveQuest,
    gentle: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    onTalk: ((String) -> Unit)? = null,
    onLeave: (() -> Unit)? = null,
) {
    val boss = q.stage.kind == StageKind.BOSS
    val accent = parseColor(q.enemy?.color ?: q.npc?.color, MaterialTheme.colorScheme.primary)
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (boss && q.enemy != null) EnemyPortrait(q.enemy, if (compact) 56.dp else 88.dp)
                else NpcPortrait(q.npc, if (compact) 48.dp else 64.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.line.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (boss && q.enemy != null) q.enemy.name else q.stage.title,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.quest_stage_of, q.stageIndex + 1, q.line.stages.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!compact && q.stage.story.isNotBlank()) {
                Text(q.stage.story, style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(goalText(q.stage), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(progressText(q), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = { q.fraction },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = if (boss) Color(0xFFE5484D) else accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                )
            }
            if (!compact) {
                hintText(q, gentle).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                rewardText(q.stage)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onTalk != null && q.npc != null) {
                        FilledTonalButton(onClick = { onTalk(q.npc.id) }) { Text(stringResource(R.string.quest_talk)) }
                    }
                    if (onLeave != null) TextButton(onClick = onLeave) { Text(stringResource(R.string.quest_leave)) }
                }
            }
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { body() }
    else Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { body() }
}

@Composable
private fun QuestBoardCard(e: QuestEntry, coins: Int, onStart: () -> Unit) {
    val locked = e.state == QuestState.LOCKED
    val done = e.state == QuestState.COMPLETED
    val content = GameContent.current
    val npc = content.npc(e.line.npc)
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NpcPortrait(npc, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.line.title, style = MaterialTheme.typography.titleMedium)
                    val meta = buildList {
                        npc?.let { add(stringResource(R.string.quest_from, it.name)) }
                        add(pluralStringResource(R.plurals.quest_stages, e.line.stages.size, e.line.stages.size))
                        if (e.line.repeatable) add(stringResource(R.string.quest_repeatable))
                    }
                    Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e.line.repeatable) Icon(Icons.Filled.Repeat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.line.summary.isNotBlank()) Text(e.line.summary, style = MaterialTheme.typography.bodyMedium)
            val times = e.progress?.timesCompleted ?: 0
            when {
                done -> Text(pluralStringResource(R.plurals.quest_completed_times, times, times), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                locked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(lockText(e.lock), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (times > 0) {
                        Text(pluralStringResource(R.plurals.quest_completed_times, times, times), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    } else Spacer(Modifier.weight(1f))
                    if (e.line.cost > 0) {
                        Button(onClick = onStart, enabled = coins >= e.line.cost) {
                            Icon(Icons.Filled.MonetizationOn, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.quest_buy_scroll, e.line.cost))
                        }
                    } else {
                        Button(onClick = onStart) { Text(stringResource(R.string.quest_accept)) }
                    }
                }
            }
        }
    }
}

/** Compact quest card for Today: the running quest, or an NPC nudging you to the board. */
@Composable
fun TodayQuestCard(board: QuestBoard?, gentle: Boolean, heroName: String, onOpen: () -> Unit) {
    val active = board?.active
    if (active != null) {
        ActiveQuestCard(active, gentle, compact = true, onClick = onOpen)
        return
    }
    val next = board?.entries?.firstOrNull { it.state == QuestState.AVAILABLE } ?: return
    val npc = GameContent.current.npc(next.line.npc)
    val line = remember(npc?.id) { npc?.dialogue?.get("greeting")?.randomOrNull() }
    Card(
        onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NpcPortrait(npc, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(npc?.name ?: next.line.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    line?.replace("{hero}", heroName) ?: next.line.summary,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.quests_open), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---- portraits -----------------------------------------------------------------------------

@Composable
fun NpcPortrait(npc: NpcDef?, size: Dp, modifier: Modifier = Modifier) {
    val sheet = remember(npc) { npc?.let { SpriteCatalog.npcSheet(it) } }
    PortraitFrame(parseColor(npc?.color, MaterialTheme.colorScheme.primary), size, modifier, npc?.name) {
        if (sheet != null) PixelSprite(sheet, SpriteCatalog.IDLE, Modifier.fillMaxSize().padding(size / 10), animate = false)
        else Initial(npc?.name)
    }
}

@Composable
fun EnemyPortrait(enemy: EnemyDef, size: Dp, modifier: Modifier = Modifier) {
    val sheet = remember(enemy) { SpriteCatalog.enemySheet(enemy) }
    PortraitFrame(parseColor(enemy.color, MaterialTheme.colorScheme.error), size, modifier, enemy.name) {
        if (sheet != null) PixelSprite(sheet, SpriteCatalog.IDLE, Modifier.fillMaxSize().padding(size / 16))
        else Initial(enemy.name)
    }
}

@Composable
fun HeroPortrait(profile: ProfileEntity?, size: Dp) {
    PortraitFrame(MaterialTheme.colorScheme.primary, size, Modifier, null) {
        org.openfocus.app.ui.components.HeroAvatar(profile, size = size, animate = false)
    }
}

@Composable
private fun PortraitFrame(color: Color, size: Dp, modifier: Modifier, description: String?, content: @Composable () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.22f))
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Initial(name: String?) {
    Text(name?.take(1)?.uppercase() ?: "?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

// ---- text helpers --------------------------------------------------------------------------

internal fun parseColor(hex: String?, fallback: Color): Color =
    hex?.removePrefix("#")?.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback

@Composable
private fun lockText(lock: QuestLock?): String = when (lock) {
    is QuestLock.Level -> stringResource(R.string.quest_locked_level, lock.level)
    is QuestLock.Requires -> stringResource(R.string.quest_locked_requires, lock.title)
    QuestLock.Busy, null -> stringResource(R.string.quest_locked_busy)
}

@Composable
private fun goalText(stage: StageDef): String = when (stage.kind) {
    StageKind.BOSS -> stringResource(R.string.quest_boss_goal)
    StageKind.COLLECT -> stage.collect!!.itemPlural.replaceFirstChar { it.uppercase() }
    StageKind.OBJECTIVE -> objectiveText(stage.objective!!)
}

@Composable
private fun progressText(q: ActiveQuest): String = when (q.stage.kind) {
    StageKind.BOSS -> stringResource(R.string.quest_boss_hp, q.current, q.target)
    StageKind.COLLECT -> stringResource(R.string.quest_objective_progress, q.current, q.target)
    StageKind.OBJECTIVE -> stringResource(R.string.quest_objective_progress, q.current.coerceIn(0, q.target), q.target)
}

@Composable
private fun hintText(q: ActiveQuest, gentle: Boolean): String = when (q.stage.kind) {
    StageKind.BOSS -> {
        val strength = q.enemy?.strength ?: 0
        listOfNotNull(
            stringResource(R.string.quest_boss_hint),
            if (strength > 0 && !gentle) stringResource(R.string.quest_boss_strikes, strength) else null,
            if (strength > 0 && gentle) stringResource(R.string.quest_boss_gentle) else null,
        ).joinToString(" ")
    }
    StageKind.COLLECT -> stringResource(R.string.quest_collect_hint)
    StageKind.OBJECTIVE -> ""
}

@Composable
private fun rewardText(stage: StageDef): String? {
    val r = stage.reward
    val parts = buildList {
        if (r.xp > 0) add(stringResource(R.string.quest_reward_xp, r.xp))
        if (r.coins > 0) add(stringResource(R.string.quest_reward_coins, r.coins))
        r.item?.let { id -> add(GameContent.current.item(id)?.name ?: id) }
    }
    return if (parts.isEmpty()) null else stringResource(R.string.quest_reward, parts.joinToString(" · "))
}

@Composable
fun objectiveText(o: ObjectiveDef): String {
    val res = (if (o.mode == ObjectiveMode.GAIN) OBJECTIVE_GAIN else OBJECTIVE_REACH)[o.stat]
    return if (res != null) stringResource(res, o.amount) else "${o.stat}: ${o.amount}"
}

/** Objective wording per stat; [org.openfocus.app.core.GameStats.STATS] must all be covered (tested). */
val OBJECTIVE_GAIN: Map<String, Int> = mapOf(
    "level" to R.string.objective_gain_level,
    "habitCompletions" to R.string.objective_gain_habitCompletions,
    "bestHabitStreak" to R.string.objective_gain_bestHabitStreak,
    "focusMinutes" to R.string.objective_gain_focusMinutes,
    "focusSessions" to R.string.objective_gain_focusSessions,
    "goalsCompleted" to R.string.objective_gain_goalsCompleted,
    "milestonesDone" to R.string.objective_gain_milestonesDone,
    "temptationsResisted" to R.string.objective_gain_temptationsResisted,
    "shortsBlocked" to R.string.objective_gain_shortsBlocked,
    "rewardsRedeemed" to R.string.objective_gain_rewardsRedeemed,
    "itemsOwned" to R.string.objective_gain_itemsOwned,
)

val OBJECTIVE_REACH: Map<String, Int> = mapOf(
    "level" to R.string.objective_reach_level,
    "habitCompletions" to R.string.objective_reach_habitCompletions,
    "bestHabitStreak" to R.string.objective_reach_bestHabitStreak,
    "focusMinutes" to R.string.objective_reach_focusMinutes,
    "focusSessions" to R.string.objective_reach_focusSessions,
    "goalsCompleted" to R.string.objective_reach_goalsCompleted,
    "milestonesDone" to R.string.objective_reach_milestonesDone,
    "temptationsResisted" to R.string.objective_reach_temptationsResisted,
    "shortsBlocked" to R.string.objective_reach_shortsBlocked,
    "rewardsRedeemed" to R.string.objective_reach_rewardsRedeemed,
    "itemsOwned" to R.string.objective_reach_itemsOwned,
)
