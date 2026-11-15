package com.openfocushq.openfocus.game

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.openfocushq.openfocus.core.Achievement
import com.openfocushq.openfocus.core.Achievements
import com.openfocushq.openfocus.core.GameRules
import com.openfocushq.openfocus.core.GameStats
import com.openfocushq.openfocus.data.db.AchievementEntity
import com.openfocushq.openfocus.data.db.ActivityEventEntity
import com.openfocushq.openfocus.data.db.AppDatabase
import com.openfocushq.openfocus.data.db.ProfileEntity

object EventTypes {
    const val HABIT_DONE = "habit_done"
    const val HABIT_UNDONE = "habit_undone"
    const val HABIT_SLIP = "habit_slip"
    const val HABIT_MISSED = "habit_missed"
    const val FOCUS_DONE = "focus_done"
    const val FOCUS_ABANDONED = "focus_abandoned"
    const val MILESTONE_DONE = "milestone_done"
    const val MILESTONE_UNDONE = "milestone_undone"
    const val GOAL_DONE = "goal_done"
    const val RESISTED = "resisted"
    const val SHORTS_BLOCKED = "shorts_blocked"
    const val APP_BLOCKED = "app_blocked"
    const val UNLOCK_BOUGHT = "unlock_bought"
    const val WAITED = "waited"
    const val ITEM_BOUGHT = "item_bought"
    const val REWARD_REDEEMED = "reward_redeemed"
    const val ACHIEVEMENT = "achievement"
    const val LEVEL_UP = "level_up"
    const val FAINTED = "fainted"
    const val PERFECT_DAY = "perfect_day"
    const val QUEST_STARTED = "quest_started"
    const val QUEST_STAGE_DONE = "quest_stage_done"
    const val QUEST_DONE = "quest_done"
    const val BOSS_STRIKE = "boss_strike"

    /** Events caused by quests themselves; quest listeners ignore these to avoid feedback loops. */
    val QUEST_EVENTS = setOf(QUEST_STARTED, QUEST_STAGE_DONE, QUEST_DONE, BOSS_STRIKE)
}

/** What just happened, as seen by listeners such as the quest engine. */
data class GameEvent(val type: String, val refId: String?, val xp: Int, val coins: Int, val hp: Int)

/** What happened as a result of an action, used by the UI to celebrate (or console). */
data class GameFeedback(
    val message: String,
    val xp: Int = 0,
    val coins: Int = 0,
    val hp: Int = 0,
    val newLevel: Int? = null,
    val achievements: List<Achievement> = emptyList(),
    val fainted: Boolean = false,
)

/**
 * The single place where XP / coins / HP change. Every change is recorded as an
 * [ActivityEventEntity] so the history is auditable and can later be synced.
 */
class GameEngine(private val db: AppDatabase) {
    private val mutex = Mutex()
    private val profiles = db.profileDao()
    private val gameDao = db.gameDao()

    private val _feedback = MutableSharedFlow<GameFeedback>(extraBufferCapacity = 16)
    val feedback: SharedFlow<GameFeedback> = _feedback.asSharedFlow()

    /** Called after every [apply] (outside the lock), e.g. so quests can react to habits and focus. */
    val listeners = java.util.concurrent.CopyOnWriteArrayList<suspend (GameEvent) -> Unit>()

    /** Show a message (snackbar) without changing any stats. */
    fun notify(message: String) { if (message.isNotBlank()) _feedback.tryEmit(GameFeedback(message)) }

    /** Guards first-launch profile creation so concurrent callers can't each create one. */
    private val createLock = Mutex()

    suspend fun profile(): ProfileEntity =
        profiles.get() ?: createLock.withLock { profiles.get() ?: ProfileEntity().also { profiles.upsert(it) } }

    suspend fun apply(
        type: String,
        refId: String? = null,
        xp: Int = 0,
        coins: Int = 0,
        hp: Int = 0,
        message: String = "",
        emit: Boolean = true,
    ): GameFeedback {
        val fb = mutex.withLock { applyLocked(type, refId, xp, coins, hp, message) }
        val withAchievements = checkAchievements(fb)
        if (emit && (withAchievements.message.isNotBlank() || withAchievements.achievements.isNotEmpty())) {
            _feedback.tryEmit(withAchievements)
        }
        val event = GameEvent(type, refId, xp, coins, hp)
        listeners.forEach { runCatching { it(event) } }
        return withAchievements
    }

    private suspend fun applyLocked(type: String, refId: String?, xp: Int, coins: Int, hp: Int, message: String): GameFeedback {
        val p = profile()
        val oldLevel = GameRules.levelFor(p.totalXp)
        val newXp = (p.totalXp + xp).coerceAtLeast(0)
        val newLevel = GameRules.levelFor(newXp)
        val maxHp = GameRules.maxHpFor(newLevel)
        var newHp = (p.hp + hp).coerceAtMost(maxHp)
        var newCoins = (p.coins + coins).coerceAtLeast(0)
        val owner = p.id
        gameDao.logEvent(ActivityEventEntity(ownerId = owner, type = type, refId = refId, xpDelta = xp, coinDelta = coins, hpDelta = hp, message = message))

        var fainted = false
        if (newLevel > oldLevel) {
            newHp = maxHp
            gameDao.logEvent(ActivityEventEntity(ownerId = owner, type = EventTypes.LEVEL_UP, message = "Reached level $newLevel"))
        }
        if (newHp <= 0) {
            fainted = true
            val penalty = GameRules.faintCoinPenalty(newCoins)
            newCoins -= penalty
            newHp = maxHp
            gameDao.logEvent(ActivityEventEntity(ownerId = owner, type = EventTypes.FAINTED, coinDelta = -penalty.toInt(), message = "Took a rest and recovered"))
        }
        profiles.upsert(p.copy(totalXp = newXp, hp = newHp, maxHp = maxHp, coins = newCoins, updatedAt = System.currentTimeMillis()))
        return GameFeedback(
            message = message, xp = xp, coins = coins, hp = hp,
            newLevel = if (newLevel > oldLevel) newLevel else null, fainted = fainted,
        )
    }

    /** Spend coins; returns false (and changes nothing) if the hero can't afford it. */
    suspend fun spend(amount: Int, type: String, refId: String?, message: String): Boolean {
        val ok = mutex.withLock {
            val p = profile()
            if (p.coins < amount) return@withLock false
            profiles.upsert(p.copy(coins = p.coins - amount, updatedAt = System.currentTimeMillis()))
            gameDao.logEvent(ActivityEventEntity(ownerId = p.id, type = type, refId = refId, coinDelta = -amount, message = message))
            true
        }
        if (ok) checkAchievements(GameFeedback(message)).let { if (it.achievements.isNotEmpty()) _feedback.tryEmit(it) }
        return ok
    }

    /** Unlocks any achievements whose conditions are already met (e.g. after an app update). */
    suspend fun syncAchievements() {
        val fb = checkAchievements(GameFeedback(""))
        if (fb.achievements.isNotEmpty()) _feedback.tryEmit(fb)
    }

    suspend fun updateProfile(transform: (ProfileEntity) -> ProfileEntity) = mutex.withLock {
        profiles.upsert(transform(profile()).copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun recordBestStreak(streak: Int) = mutex.withLock {
        val p = profile()
        if (streak > p.bestStreak) profiles.upsert(p.copy(bestStreak = streak))
    }

    suspend fun stats(): GameStats {
        val p = profile()
        return GameStats(
            level = GameRules.levelFor(p.totalXp),
            habitCompletions = db.habitDao().totalCompletions(),
            bestHabitStreak = p.bestStreak,
            focusMinutes = db.focusDao().totalCompletedMinutes(),
            focusSessions = db.focusDao().completedCount(),
            goalsCompleted = db.goalDao().completedCount(),
            milestonesDone = db.goalDao().milestonesDone(),
            temptationsResisted = gameDao.countEvents(EventTypes.RESISTED),
            shortsBlocked = gameDao.countEvents(EventTypes.SHORTS_BLOCKED),
            rewardsRedeemed = gameDao.countEvents(EventTypes.REWARD_REDEEMED),
            itemsOwned = gameDao.countEvents(EventTypes.ITEM_BOUGHT),
        )
    }

    private suspend fun checkAchievements(fb: GameFeedback): GameFeedback {
        val unlocked = gameDao.achievementIds().toSet()
        val fresh = Achievements.newlyMet(stats(), unlocked)
        if (fresh.isEmpty()) return fb
        var level = fb.newLevel
        for (a in fresh) {
            if (gameDao.unlock(AchievementEntity(a.id)) == -1L) continue
            val r = mutex.withLock {
                applyLocked(EventTypes.ACHIEVEMENT, a.id, a.xpReward, a.coinReward, 0, "Achievement unlocked: ${a.title}")
            }
            if (r.newLevel != null) level = r.newLevel
        }
        return fb.copy(achievements = fresh, newLevel = level)
    }
}
