package org.openfocus.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProfileEntity::class, HabitEntity::class, HabitLogEntity::class, GoalEntity::class,
        MilestoneEntity::class, FocusSessionEntity::class, BlockRuleEntity::class,
        BlockScheduleEntity::class, AchievementEntity::class, RewardEntity::class,
        InventoryEntity::class, ActivityEventEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // v2: profiles.skinTone
        AutoMigration(from = 2, to = 3), // v3: block_rules.gate / waitSeconds / allowMinutes
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun habitDao(): HabitDao
    abstract fun goalDao(): GoalDao
    abstract fun focusDao(): FocusDao
    abstract fun blockDao(): BlockDao
    abstract fun gameDao(): GameDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "openfocus.db").build()
    }
}
