package org.openfocus.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.openfocus.app.core.Days
import org.openfocus.app.core.Encouragement
import org.openfocus.app.core.GameRules
import org.openfocus.app.core.HabitSchedule
import org.openfocus.app.data.db.AppDatabase
import org.openfocus.app.data.db.HabitEntity
import org.openfocus.app.data.db.HabitKind
import org.openfocus.app.data.db.HabitLogEntity
import org.openfocus.app.data.prefs.SettingsStore
import org.openfocus.app.game.EventTypes
import org.openfocus.app.game.GameEngine

data class HabitState(
    val habit: HabitEntity,
    val today: Long,
    val todayCount: Int,
    val periodProgress: Int,
    val satisfied: Boolean,
    val scheduledToday: Boolean,
    val streak: Int,
    val counts: Map<Long, Int>,
) {
    val isBreak get() = habit.kind == HabitKind.BREAK
}

class HabitRepository(
    private val db: AppDatabase,
    private val game: GameEngine,
    private val settings: SettingsStore,
) {
    private val dao = db.habitDao()

    /** How far back we load history for streaks / heatmaps. */
    private val historyDays = 400L

    fun observeStates(): Flow<List<HabitState>> {
        val today = Days.today()
        return combine(dao.observeActive(), dao.observeLogs(today - historyDays, today + 1)) { habits, logs ->
            val byHabit = logs.groupBy { it.habitId }
            habits.map { h -> stateOf(h, byHabit[h.id].orEmpty(), today) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeState(id: String): Flow<HabitState?> = dao.observe(id).flatMapLatest { h ->
        if (h == null) flowOf(null)
        else dao.observeLogsFor(id).let { logsFlow ->
            combine(logsFlow, flowOf(h)) { logs, habit -> stateOf(habit, logs, Days.today()) }
        }
    }

    private fun stateOf(h: HabitEntity, logs: List<HabitLogEntity>, today: Long): HabitState {
        val counts = logs.associate { it.epochDay to it.count }
        return HabitState(
            habit = h,
            today = today,
            todayCount = counts[today] ?: 0,
            periodProgress = HabitSchedule.progressFor(h, counts, today),
            satisfied = HabitSchedule.isSatisfied(h, counts, today),
            scheduledToday = HabitSchedule.isScheduledOn(h, today),
            streak = HabitSchedule.streak(h, counts, today),
            counts = counts,
        )
    }

    suspend fun get(id: String) = dao.get(id)

    suspend fun save(habit: HabitEntity) {
        val owner = game.profile().id
        dao.upsert(habit.copy(ownerId = owner, updatedAt = System.currentTimeMillis()))
    }

    suspend fun archive(id: String) {
        dao.get(id)?.let { dao.upsert(it.copy(archived = true, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun delete(id: String) {
        dao.get(id)?.let { dao.upsert(it.copy(deleted = true, updatedAt = System.currentTimeMillis())) }
    }

    private suspend fun counts(id: String) = dao.logsFor(id).associate { it.epochDay to it.count }

    /** Tap on a habit: completes a BUILD habit, or records a slip for a BREAK habit. */
    suspend fun tap(id: String) {
        val h = dao.get(id) ?: return
        val today = Days.today()
        val existing = dao.log(id, today)
        val before = counts(id)
        val wasSatisfied = HabitSchedule.isSatisfied(h, before, today)
        val newCount = (existing?.count ?: 0) + 1
        dao.upsertLog(existing?.copy(count = newCount, updatedAt = System.currentTimeMillis())
            ?: HabitLogEntity(habitId = id, epochDay = today, count = newCount))

        if (h.kind == HabitKind.BREAK) {
            val dmg = if (settings.value.gentleMode) 1 else GameRules.slipDamage(h.difficulty)
            game.apply(EventTypes.HABIT_SLIP, id, hp = -dmg, message = "Slip logged. Noticing is the first step — you've got this.")
            return
        }
        if (wasSatisfied) return // extra reps beyond the target are welcome but not rewarded
        val after = before + (today to newCount)
        val streak = HabitSchedule.streak(h, after, today)
        val xp = GameRules.habitXp(h.difficulty, streak)
        game.recordBestStreak(streak)
        game.apply(EventTypes.HABIT_DONE, id, xp = xp, coins = GameRules.habitCoins(xp), message = Encouragement.afterHabit(streak))
    }

    /** Undo the last tap for today. */
    suspend fun untap(id: String) {
        val h = dao.get(id) ?: return
        val today = Days.today()
        val existing = dao.log(id, today) ?: return
        val before = counts(id)
        val streakBefore = HabitSchedule.streak(h, before, today)
        val wasSatisfied = HabitSchedule.isSatisfied(h, before, today)
        if (existing.count <= 1) dao.deleteLog(existing)
        else dao.upsertLog(existing.copy(count = existing.count - 1, updatedAt = System.currentTimeMillis()))
        val after = counts(id)

        if (h.kind == HabitKind.BREAK) {
            val dmg = if (settings.value.gentleMode) 1 else GameRules.slipDamage(h.difficulty)
            game.apply(EventTypes.HABIT_UNDONE, id, hp = dmg, emit = false)
            return
        }
        if (wasSatisfied && !HabitSchedule.isSatisfied(h, after, today)) {
            val xp = GameRules.habitXp(h.difficulty, streakBefore)
            game.apply(EventTypes.HABIT_UNDONE, id, xp = -xp, coins = -GameRules.habitCoins(xp), emit = false)
        }
    }

    /**
     * Habitica-style "cron": for each fully elapsed day since the last run, apply damage for missed
     * scheduled habits and track the perfect-day streak. Gentle mode skips the damage.
     */
    suspend fun rollover() {
        val today = Days.today()
        val profile = game.profile()
        if (profile.lastRolloverDay == 0L) {
            game.updateProfile { it.copy(lastRolloverDay = today - 1) }
            return
        }
        if (profile.lastRolloverDay >= today - 1) return
        val from = maxOf(profile.lastRolloverDay + 1, today - 7)
        val habits = dao.active()
        val logs = dao.logs(from - 7, today).groupBy { it.habitId }
        val gentle = settings.value.gentleMode
        var totalDamage = 0
        var missed = 0
        var perfectStreak = profile.currentStreak
        for (day in from until today) {
            var dayMissed = 0
            var anyDue = false
            for (h in habits) {
                val counts = logs[h.id].orEmpty().associate { it.epochDay to it.count }
                if (h.kind == HabitKind.BUILD && HabitSchedule.isScheduledOn(h, day)) anyDue = true
                if (HabitSchedule.missedOn(h, counts, day)) {
                    dayMissed++
                    if (!gentle) totalDamage += GameRules.missDamage(h.difficulty)
                }
            }
            missed += dayMissed
            perfectStreak = if (anyDue && dayMissed == 0) perfectStreak + 1 else if (dayMissed > 0) 0 else perfectStreak
        }
        game.updateProfile {
            it.copy(lastRolloverDay = today - 1, currentStreak = perfectStreak)
        }
        if (missed > 0) {
            val msg = if (gentle) "Yesterday got away from you ($missed missed). New day, fresh start!"
            else "You missed $missed habit${if (missed > 1) "s" else ""} and lost $totalDamage HP. " + Encouragement.comeback()
            game.apply(EventTypes.HABIT_MISSED, null, hp = -totalDamage, message = msg)
        } else if (perfectStreak > 0) {
            game.apply(EventTypes.PERFECT_DAY, null, xp = 10, coins = 3, message = "Perfect day streak: $perfectStreak!")
        }
    }

    suspend fun pendingTodayCount(): Int {
        val today = Days.today()
        val logs = dao.logs(today - 7, today).groupBy { it.habitId }
        return dao.active().count { h ->
            h.kind == HabitKind.BUILD && HabitSchedule.isScheduledOn(h, today) &&
                !HabitSchedule.isSatisfied(h, logs[h.id].orEmpty().associate { it.epochDay to it.count }, today)
        }
    }
}
