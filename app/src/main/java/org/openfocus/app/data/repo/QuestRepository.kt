package org.openfocus.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openfocus.app.core.GameRules
import org.openfocus.app.core.content.DialogueLine
import org.openfocus.app.core.content.EnemyDef
import org.openfocus.app.core.content.GameContent
import org.openfocus.app.core.content.GameContentData
import org.openfocus.app.core.content.NpcDef
import org.openfocus.app.core.content.ObjectiveMode
import org.openfocus.app.core.content.QuestLineDef
import org.openfocus.app.core.content.StageDef
import org.openfocus.app.core.content.StageKind
import org.openfocus.app.core.content.withHero
import org.openfocus.app.data.db.AppDatabase
import org.openfocus.app.data.db.InventoryEntity
import org.openfocus.app.data.db.QuestProgressEntity
import org.openfocus.app.R
import org.openfocus.app.core.Strings
import org.openfocus.app.data.db.QuestStatus
import org.openfocus.app.data.prefs.SettingsStore
import org.openfocus.app.game.EventTypes
import org.openfocus.app.game.GameEngine
import org.openfocus.app.game.GameEvent
import kotlin.random.Random

enum class QuestState { LOCKED, AVAILABLE, ACTIVE, COMPLETED }

/** A quest line as the quest board shows it. */
data class QuestEntry(
    val line: QuestLineDef,
    val state: QuestState,
    val progress: QuestProgressEntity?,
    /** Why a locked quest can't be started yet. */
    val lock: QuestLock? = null,
)

/** Why a quest line is locked; the UI turns it into localised text. */
sealed interface QuestLock {
    data class Level(val level: Int) : QuestLock
    data class Requires(val title: String) : QuestLock
    data object Busy : QuestLock
}

/** The running quest's current stage, ready to render. */
data class ActiveQuest(
    val line: QuestLineDef,
    val stageIndex: Int,
    val stage: StageDef,
    val npc: NpcDef?,
    val enemy: EnemyDef?,
    /** Boss: HP left. Collect: items found. Objective: progress so far. */
    val current: Int,
    /** Boss: max HP. Collect/objective: amount needed. */
    val target: Int,
) {
    val fraction: Float get() = if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
}

data class QuestBoard(val active: ActiveQuest?, val entries: List<QuestEntry>)

/** A conversation to show (NPC/boss/hero lines), already with `{hero}` filled in. */
data class Conversation(val title: String, val lines: List<DialogueLine>)

/**
 * Habitica-style quests from content.json. Listens to every game event: habits, focus, resisting
 * and milestones damage bosses or drop collectables; missed dailies and slips let bosses strike
 * back (not in gentle mode); objective stages complete when their stat goal is met.
 */
class QuestRepository(
    private val db: AppDatabase,
    private val game: GameEngine,
    private val settings: SettingsStore,
    private val strings: Strings,
    private val content: () -> GameContentData = { GameContent.current },
    private val random: Random = Random.Default,
) {
    private val dao = db.questDao()
    private val mutex = Mutex()

    private val _conversations = MutableSharedFlow<Conversation>(extraBufferCapacity = 8)
    val conversations: SharedFlow<Conversation> = _conversations.asSharedFlow()

    fun observeBoard(): Flow<QuestBoard> =
        combine(dao.observeAll(), db.profileDao().observe()) { rows, _ -> rows }.map { rows -> board(rows) }

    private suspend fun board(rows: List<QuestProgressEntity>): QuestBoard {
        val c = content()
        val byId = rows.associateBy { it.lineId }
        val level = GameRules.levelFor(game.profile().totalXp)
        val anyActive = rows.any { it.status == QuestStatus.ACTIVE }
        val entries = c.questLines.map { line ->
            val p = byId[line.id]
            val done = p?.timesCompleted ?: 0
            when {
                p?.status == QuestStatus.ACTIVE -> QuestEntry(line, QuestState.ACTIVE, p)
                done > 0 && !line.repeatable -> QuestEntry(line, QuestState.COMPLETED, p)
                level < line.unlockLevel -> QuestEntry(line, QuestState.LOCKED, p, QuestLock.Level(line.unlockLevel))
                line.requires != null && (byId[line.requires]?.timesCompleted ?: 0) == 0 ->
                    QuestEntry(line, QuestState.LOCKED, p, QuestLock.Requires(c.questLine(line.requires)?.title ?: line.requires))
                anyActive -> QuestEntry(line, QuestState.LOCKED, p, QuestLock.Busy)
                else -> QuestEntry(line, QuestState.AVAILABLE, p)
            }
        }
        return QuestBoard(rows.firstOrNull { it.status == QuestStatus.ACTIVE }?.let { activeView(it) }, entries)
    }

    private suspend fun activeView(p: QuestProgressEntity): ActiveQuest? {
        val c = content()
        val line = c.questLine(p.lineId) ?: return null
        val stage = line.stages.getOrNull(p.stageIndex) ?: return null
        val enemy = c.enemy(stage.boss)
        val (current, target) = when (stage.kind) {
            StageKind.BOSS -> ((enemy?.hp ?: 0) - p.progress).coerceAtLeast(0) to (enemy?.hp ?: 0)
            StageKind.COLLECT -> p.progress to (stage.collect?.amount ?: 0)
            StageKind.OBJECTIVE -> objectiveProgress(stage, p) to (stage.objective?.amount ?: 0)
        }
        return ActiveQuest(line, p.stageIndex, stage, c.npc(line.npc), enemy, current, target)
    }

    private suspend fun objectiveProgress(stage: StageDef, p: QuestProgressEntity): Int {
        val o = stage.objective ?: return 0
        val value = game.stats().value(o.stat)
        return if (o.mode == ObjectiveMode.GAIN) value - p.baseline else value
    }

    /** Accept a quest from the board (buying its scroll if it costs coins). */
    suspend fun accept(lineId: String): Boolean = mutex.withLock {
        val c = content()
        val line = c.questLine(lineId) ?: return@withLock false
        val entry = board(currentRows()).entries.firstOrNull { it.line.id == lineId } ?: return@withLock false
        if (entry.state != QuestState.AVAILABLE) return@withLock false
        if (line.cost > 0 && !game.spend(line.cost, EventTypes.QUEST_STARTED, lineId, strings.get(R.string.quest_scroll_bought, line.title))) return@withLock false
        val prev = dao.get(lineId)
        val p = QuestProgressEntity(
            lineId = lineId, stageIndex = 0, progress = 0, baseline = baselineFor(line.stages.first()),
            status = QuestStatus.ACTIVE, timesCompleted = prev?.timesCompleted ?: 0,
        )
        dao.upsert(p)
        game.apply(EventTypes.QUEST_STARTED, lineId, message = strings.get(R.string.quest_started, line.title), emit = false)
        _conversations.tryEmit(Conversation(line.title, stageOpening(line.stages.first())))
        checkObjective(line, p)
        true
    }

    /** Show a random line from an NPC's (or enemy's) dialogue, e.g. "idle" when the player taps Talk. */
    suspend fun chat(speakerId: String, key: String = "idle"): Boolean {
        val c = content()
        val lines = c.npc(speakerId)?.dialogue?.get(key) ?: c.enemy(speakerId)?.dialogue?.get(key) ?: return false
        val text = lines.randomOrNull(random) ?: return false
        val title = c.npc(speakerId)?.name ?: c.enemy(speakerId)?.name ?: ""
        return _conversations.tryEmit(Conversation(title, withHero(listOf(DialogueLine(speakerId, text)))))
    }

    /** Give up the current quest (Habitica lets you leave a quest; progress is lost). */
    suspend fun abandon() = mutex.withLock {
        val p = dao.active() ?: return@withLock
        dao.upsert(p.copy(status = QuestStatus.ABANDONED, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun currentRows() = db.questDao().let { d -> content().questLines.mapNotNull { d.get(it.id) } }

    private suspend fun baselineFor(stage: StageDef): Int = stage.objective?.let { game.stats().value(it.stat) } ?: 0

    private suspend fun heroName() = game.profile().displayName

    private suspend fun stageOpening(stage: StageDef): List<DialogueLine> {
        val enemy = content().enemy(stage.boss)
        val appear = enemy?.dialogue?.get("appear")?.firstOrNull()?.let { DialogueLine(enemy.id, it) }
        return withHero(stage.intro + listOfNotNull(appear.takeIf { stage.intro.none { l -> l.speaker == enemy?.id } }))
    }

    private suspend fun withHero(lines: List<DialogueLine>): List<DialogueLine> {
        val name = heroName()
        return lines.map { it.copy(text = it.text.withHero(name)) }
    }

    /** Game event hook (registered in AppContainer). */
    suspend fun onEvent(e: GameEvent) {
        if (e.type in EventTypes.QUEST_EVENTS) return // our own rewards / strikes: ignore (and avoid re-entering the lock)
        mutex.withLock {
            val p = dao.active() ?: return@withLock
            val line = content().questLine(p.lineId) ?: return@withLock
            val stage = line.stages.getOrNull(p.stageIndex) ?: return@withLock
            when (stage.kind) {
                StageKind.BOSS -> bossEvent(line, stage, p, e)
                StageKind.COLLECT -> collectEvent(line, stage, p, e)
                StageKind.OBJECTIVE -> checkObjective(line, p)
            }
        }
    }

    private suspend fun bossEvent(line: QuestLineDef, stage: StageDef, p: QuestProgressEntity, e: GameEvent) {
        val enemy = content().enemy(stage.boss) ?: return
        val damage = when (e.type) {
            EventTypes.HABIT_DONE -> GameRules.habitDamage(e.xp)
            EventTypes.FOCUS_DONE -> GameRules.focusDamage(e.xp)
            EventTypes.RESISTED -> GameRules.RESIST_DAMAGE
            EventTypes.MILESTONE_DONE -> GameRules.MILESTONE_DAMAGE
            EventTypes.GOAL_DONE -> GameRules.GOAL_DAMAGE
            else -> 0
        }
        if (damage > 0) {
            val dealt = (p.progress + damage).coerceAtMost(enemy.hp)
            val updated = p.copy(progress = dealt, updatedAt = System.currentTimeMillis())
            dao.upsert(updated)
            game.notify(strings.get(R.string.quest_hit, enemy.name, damage, enemy.hp - dealt, enemy.hp))
            if (dealt >= enemy.hp) completeStage(line, updated)
            return
        }
        val hits = when (e.type) {
            EventTypes.HABIT_SLIP -> 1
            EventTypes.HABIT_MISSED -> e.refId?.toIntOrNull() ?: 0
            else -> 0
        }
        if (hits > 0 && enemy.strength > 0 && !settings.value.gentleMode) {
            val taunt = enemy.dialogue["hit"]?.randomOrNull(random)?.withHero(heroName())
            game.apply(EventTypes.BOSS_STRIKE, enemy.id, hp = -enemy.strength * hits,
                message = if (taunt != null) strings.get(R.string.quest_strike_taunt, enemy.name, enemy.strength * hits, taunt)
                else strings.get(R.string.quest_strike, enemy.name, enemy.strength * hits))
        }
    }

    private suspend fun collectEvent(line: QuestLineDef, stage: StageDef, p: QuestProgressEntity, e: GameEvent) {
        val collect = stage.collect ?: return
        val chance = GameRules.dropChance(e.type)
        if (chance <= 0.0 || random.nextDouble() >= chance) return
        val found = (p.progress + 1).coerceAtMost(collect.amount)
        val updated = p.copy(progress = found, updatedAt = System.currentTimeMillis())
        dao.upsert(updated)
        game.notify(strings.get(R.string.quest_found, collect.item, found, collect.amount))
        if (found >= collect.amount) completeStage(line, updated)
    }

    private suspend fun checkObjective(line: QuestLineDef, p: QuestProgressEntity) {
        val stage = line.stages.getOrNull(p.stageIndex) ?: return
        val o = stage.objective ?: return
        if (stage.kind == StageKind.OBJECTIVE && objectiveProgress(stage, p) >= o.amount) completeStage(line, p)
    }

    private suspend fun completeStage(line: QuestLineDef, p: QuestProgressEntity) {
        val stage = line.stages[p.stageIndex]
        val reward = stage.reward
        reward.item?.let { db.gameDao().addItem(InventoryEntity(it)) }
        game.apply(EventTypes.QUEST_STAGE_DONE, "${line.id}/${stage.id}", xp = reward.xp, coins = reward.coins,
            message = reward.item?.let { id -> strings.get(R.string.quest_stage_done_item, stage.title, content().item(id)?.name ?: id) }
                ?: strings.get(R.string.quest_stage_done, stage.title))
        val enemy = content().enemy(stage.boss)
        val defeat = enemy?.dialogue?.get("defeat")?.firstOrNull()?.let { DialogueLine(enemy.id, it) }
        val outro = listOfNotNull(defeat) + stage.outro
        val next = line.stages.getOrNull(p.stageIndex + 1)
        if (next != null) {
            val advanced = p.copy(stageIndex = p.stageIndex + 1, progress = 0, baseline = baselineFor(next), updatedAt = System.currentTimeMillis())
            dao.upsert(advanced)
            _conversations.tryEmit(Conversation(line.title, withHero(outro) + stageOpening(next)))
            checkObjective(line, advanced)
        } else {
            dao.upsert(p.copy(status = QuestStatus.COMPLETED, completedAt = System.currentTimeMillis(),
                timesCompleted = p.timesCompleted + 1, updatedAt = System.currentTimeMillis()))
            game.apply(EventTypes.QUEST_DONE, line.id, message = strings.get(R.string.quest_done, line.title))
            if (outro.isNotEmpty()) _conversations.tryEmit(Conversation(line.title, withHero(outro)))
        }
    }
}
