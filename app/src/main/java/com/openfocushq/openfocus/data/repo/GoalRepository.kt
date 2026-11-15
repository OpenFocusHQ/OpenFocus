package com.openfocushq.openfocus.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.openfocushq.openfocus.core.GameRules
import com.openfocushq.openfocus.data.db.AppDatabase
import com.openfocushq.openfocus.data.db.GoalEntity
import com.openfocushq.openfocus.data.db.GoalStatus
import com.openfocushq.openfocus.data.db.MilestoneEntity
import com.openfocushq.openfocus.game.EventTypes
import com.openfocushq.openfocus.game.GameEngine

data class GoalState(
    val goal: GoalEntity,
    val milestones: List<MilestoneEntity>,
    val habitCompletions: Int = 0,
    val focusMinutes: Int = 0,
) {
    val done get() = milestones.count { it.done }
    val progress: Float get() = when {
        goal.status == GoalStatus.COMPLETED -> 1f
        milestones.isEmpty() -> 0f
        else -> done.toFloat() / milestones.size
    }
}

class GoalRepository(private val db: AppDatabase, private val game: GameEngine) {
    private val dao = db.goalDao()

    fun observeGoals(): Flow<List<GoalState>> =
        combine(dao.observeAll(), dao.observeAllMilestones()) { goals, ms ->
            val byGoal = ms.groupBy { it.goalId }
            goals.map { GoalState(it, byGoal[it.id].orEmpty()) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeGoal(id: String): Flow<GoalState?> = dao.observe(id).flatMapLatest { g ->
        if (g == null) flowOf(null)
        else combine(
            dao.observeMilestones(id),
            db.habitDao().observeCompletionsForGoal(id),
            db.focusDao().observeMinutesForGoal(id),
        ) { ms, completions, minutes -> GoalState(g, ms, completions, minutes) }
    }

    suspend fun save(goal: GoalEntity, newMilestones: List<String> = emptyList()) {
        val owner = game.profile().id
        dao.upsert(goal.copy(ownerId = owner, updatedAt = System.currentTimeMillis()))
        newMilestones.filter { it.isNotBlank() }.forEachIndexed { i, t ->
            dao.upsertMilestone(MilestoneEntity(goalId = goal.id, title = t.trim(), sortOrder = i))
        }
    }

    suspend fun addMilestone(goalId: String, title: String, order: Int) {
        if (title.isBlank()) return
        dao.upsertMilestone(MilestoneEntity(goalId = goalId, title = title.trim(), sortOrder = order))
    }

    suspend fun deleteMilestone(m: MilestoneEntity) = dao.deleteMilestone(m)

    suspend fun toggleMilestone(id: String) {
        val m = dao.milestone(id) ?: return
        val nowDone = !m.done
        dao.upsertMilestone(m.copy(done = nowDone, doneAt = if (nowDone) System.currentTimeMillis() else null, updatedAt = System.currentTimeMillis()))
        if (nowDone) {
            game.apply(EventTypes.MILESTONE_DONE, id, xp = GameRules.MILESTONE_XP, coins = GameRules.MILESTONE_COINS,
                message = "Camp reached: ${m.title}. The summit is getting closer!")
        } else {
            game.apply(EventTypes.MILESTONE_UNDONE, id, xp = -GameRules.MILESTONE_XP, coins = -GameRules.MILESTONE_COINS, emit = false)
        }
    }

    suspend fun complete(goalId: String) {
        val g = dao.get(goalId) ?: return
        if (g.status == GoalStatus.COMPLETED) return
        dao.upsert(g.copy(status = GoalStatus.COMPLETED, completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        game.apply(EventTypes.GOAL_DONE, goalId, xp = GameRules.GOAL_XP, coins = GameRules.GOAL_COINS,
            message = "Summit reached: ${g.title}! Take a moment to enjoy the view.")
    }

    suspend fun setStatus(goalId: String, status: GoalStatus) {
        val g = dao.get(goalId) ?: return
        dao.upsert(g.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(goalId: String) {
        val g = dao.get(goalId) ?: return
        dao.upsert(g.copy(deleted = true, updatedAt = System.currentTimeMillis()))
    }
}
