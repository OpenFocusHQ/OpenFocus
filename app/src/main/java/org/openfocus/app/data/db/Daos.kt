package org.openfocus.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles LIMIT 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles LIMIT 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deleted = 0 AND archived = 0 ORDER BY sortOrder, createdAt")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE deleted = 0 AND archived = 0")
    suspend fun active(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observe(id: String): Flow<HabitEntity?>

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("SELECT * FROM habit_logs WHERE epochDay BETWEEN :from AND :to")
    fun observeLogs(from: Long, to: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE epochDay BETWEEN :from AND :to")
    suspend fun logs(from: Long, to: Long): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY epochDay DESC")
    fun observeLogsFor(habitId: String): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY epochDay DESC")
    suspend fun logsFor(habitId: String): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND epochDay = :day")
    suspend fun log(habitId: String, day: Long): HabitLogEntity?

    @Upsert
    suspend fun upsertLog(log: HabitLogEntity)

    @Delete
    suspend fun deleteLog(log: HabitLogEntity)

    @Query("SELECT COALESCE(SUM(l.count),0) FROM habit_logs l JOIN habits h ON h.id = l.habitId WHERE h.kind = 'BUILD'")
    suspend fun totalCompletions(): Int

    @Query("SELECT COALESCE(SUM(l.count),0) FROM habit_logs l JOIN habits h ON h.id = l.habitId WHERE h.goalId = :goalId AND h.kind = 'BUILD'")
    fun observeCompletionsForGoal(goalId: String): Flow<Int>
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE deleted = 0 ORDER BY status, createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    fun observe(id: String): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: String): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Query("SELECT * FROM milestones ORDER BY sortOrder")
    fun observeAllMilestones(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE goalId = :goalId ORDER BY sortOrder")
    fun observeMilestones(goalId: String): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE id = :id")
    suspend fun milestone(id: String): MilestoneEntity?

    @Upsert
    suspend fun upsertMilestone(m: MilestoneEntity)

    @Delete
    suspend fun deleteMilestone(m: MilestoneEntity)

    @Query("SELECT COUNT(*) FROM goals WHERE status = 'COMPLETED' AND deleted = 0")
    suspend fun completedCount(): Int

    @Query("SELECT COUNT(*) FROM milestones WHERE done = 1")
    suspend fun milestonesDone(): Int
}

@Dao
interface FocusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt")
    fun observeSince(since: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(actualMinutes),0) FROM focus_sessions WHERE outcome = 'COMPLETED'")
    suspend fun totalCompletedMinutes(): Int

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE outcome = 'COMPLETED'")
    suspend fun completedCount(): Int

    @Query("SELECT COALESCE(SUM(actualMinutes),0) FROM focus_sessions WHERE goalId = :goalId AND outcome = 'COMPLETED'")
    fun observeMinutesForGoal(goalId: String): Flow<Int>
}

@Dao
interface BlockDao {
    @Query("SELECT * FROM block_rules ORDER BY label")
    fun observeRules(): Flow<List<BlockRuleEntity>>

    @Upsert
    suspend fun upsertRule(rule: BlockRuleEntity)

    @Query("DELETE FROM block_rules WHERE packageName = :pkg")
    suspend fun deleteRule(pkg: String)

    @Query("SELECT * FROM block_schedules ORDER BY startMinute")
    fun observeSchedules(): Flow<List<BlockScheduleEntity>>

    @Upsert
    suspend fun upsertSchedule(s: BlockScheduleEntity)

    @Delete
    suspend fun deleteSchedule(s: BlockScheduleEntity)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM achievements")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT achievementId FROM achievements")
    suspend fun achievementIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(a: AchievementEntity): Long

    @Query("SELECT * FROM rewards WHERE deleted = 0 ORDER BY cost")
    fun observeRewards(): Flow<List<RewardEntity>>

    @Upsert
    suspend fun upsertReward(r: RewardEntity)

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun reward(id: String): RewardEntity?

    @Query("SELECT * FROM inventory")
    fun observeInventory(): Flow<List<InventoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(i: InventoryEntity)

    @Insert
    suspend fun logEvent(e: ActivityEventEntity)

    @Query("SELECT * FROM activity_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeEvents(limit: Int = 100): Flow<List<ActivityEventEntity>>

    @Query("SELECT COUNT(*) FROM activity_events WHERE type = :type")
    suspend fun countEvents(type: String): Int

    @Query("SELECT * FROM activity_events WHERE syncedAt IS NULL ORDER BY timestamp LIMIT :limit")
    suspend fun unsynced(limit: Int = 200): List<ActivityEventEntity>

    @Query("UPDATE activity_events SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)
}
