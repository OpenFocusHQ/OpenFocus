package com.openfocushq.openfocus.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * Multiplayer-ready conventions used by every synced entity:
 *  - `id` is a client-generated UUID string (no auto-increment ids that would clash across devices).
 *  - `ownerId` points at the Profile that owns the row (today: always the single local profile).
 *  - `updatedAt` is a wall-clock millis stamp suitable for last-writer-wins sync.
 *  - `deleted` is a tombstone flag so deletes can be propagated instead of silently vanishing.
 * See ARCHITECTURE.md for how a future sync/party/challenge layer plugs in.
 */

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String = newId(),
    val displayName: String = "Adventurer",
    val totalXp: Long = 0,
    val hp: Int = 50,
    val maxHp: Int = 50,
    val coins: Long = 0,
    /** Best ever "perfect day" streak. */
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    /** Epoch day of the last day-rollover that was processed. */
    val lastRolloverDay: Long = 0,
    val avatarColor: String? = null,
    val avatarHat: String? = null,
    val avatarBackdrop: String? = null,
    /** Pixel-art skin tone id ("light" | "medium" | "deep"); null = light. Added in DB v2. */
    val skinTone: String? = null,
    /**
     * Equipped cosmetics as a JSON object {slotId: itemId}, so content can add slots without
     * schema changes. Added in DB v5; empty means "use the legacy avatar* columns".
     */
    @ColumnInfo(defaultValue = "") val equipped: String = "",
    /** "local" today; a remote account id once sync exists. */
    val accountKind: String = "local",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class HabitKind { BUILD, BREAK }
enum class HabitFrequency { DAILY, WEEKLY, SPECIFIC_DAYS }
enum class Difficulty(val label: String, val multiplier: Double) {
    TRIVIAL("Trivial", 0.5), EASY("Easy", 1.0), MEDIUM("Medium", 1.6), HARD("Hard", 2.4)
}

@Entity(tableName = "habits", indices = [Index("ownerId"), Index("goalId")])
data class HabitEntity(
    @PrimaryKey val id: String = newId(),
    val ownerId: String,
    val title: String,
    val notes: String = "",
    val icon: String = "Star",
    val colorIndex: Int = 0,
    val kind: HabitKind = HabitKind.BUILD,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    /** For WEEKLY: number of completions per week. For DAILY: completions per day (usually 1). */
    val target: Int = 1,
    /** For SPECIFIC_DAYS: bit 0 = Monday ... bit 6 = Sunday. */
    val daysMask: Int = 0b1111111,
    val difficulty: Difficulty = Difficulty.EASY,
    val goalId: String? = null,
    val reminderMinuteOfDay: Int? = null,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "epochDay"], unique = true)],
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class HabitLogEntity(
    @PrimaryKey val id: String = newId(),
    val habitId: String,
    val epochDay: Long,
    /** Completions (BUILD) or slips (BREAK) on that day. */
    val count: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class GoalStatus { ACTIVE, COMPLETED, PAUSED }

@Entity(tableName = "goals", indices = [Index("ownerId")])
data class GoalEntity(
    @PrimaryKey val id: String = newId(),
    val ownerId: String,
    val title: String,
    val why: String = "",
    val targetEpochDay: Long? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val colorIndex: Int = 0,
    /** Free-form "summit" name shown in the ascent view. */
    val summitName: String = "",
    val deleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(
    tableName = "milestones",
    indices = [Index("goalId")],
    foreignKeys = [ForeignKey(
        entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class MilestoneEntity(
    @PrimaryKey val id: String = newId(),
    val goalId: String,
    val title: String,
    val sortOrder: Int = 0,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class FocusOutcome { COMPLETED, ABANDONED }

@Entity(tableName = "focus_sessions", indices = [Index("ownerId")])
data class FocusSessionEntity(
    @PrimaryKey val id: String = newId(),
    val ownerId: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val outcome: FocusOutcome,
    val label: String = "",
    val goalId: String? = null,
    val deepFocus: Boolean = false,
)

enum class BlockMode {
    /** Blocked any time the app is opened. */
    ALWAYS,
    /** Allowed until today's usage passes [BlockRuleEntity.dailyLimitMinutes]. */
    DAILY_LIMIT,
    /** Only blocked during a focus session or an active schedule. */
    FOCUS_AND_SCHEDULE,
}

/** What the pause screen does when a rule blocks an app. */
enum class BlockGate {
    /** No way in except closing the app (or paying coins for an unlock pass, unless strict). */
    HARD,
    /** Wait [BlockRuleEntity.waitSeconds] on the pause screen, then the app opens for [BlockRuleEntity.allowMinutes]. */
    WAIT,
}

@Entity(tableName = "block_rules")
data class BlockRuleEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val mode: BlockMode = BlockMode.FOCUS_AND_SCHEDULE,
    val dailyLimitMinutes: Int = 30,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    // Added in DB v3. Rows migrated from v2 keep their old behaviour (HARD); new rules default to WAIT.
    @ColumnInfo(defaultValue = "HARD") val gate: BlockGate = BlockGate.WAIT,
    @ColumnInfo(defaultValue = "30") val waitSeconds: Int = 30,
    @ColumnInfo(defaultValue = "10") val allowMinutes: Int = 10,
    /** Added in DB v4: whether coins may skip the wait / buy a pass for this app. */
    @ColumnInfo(defaultValue = "1") val allowCoins: Boolean = true,
)

@Entity(tableName = "block_schedules")
data class BlockScheduleEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    /** bit 0 = Monday ... bit 6 = Sunday. */
    val daysMask: Int = 0b1111111,
    val enabled: Boolean = true,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val achievementId: String,
    val unlockedAt: Long = System.currentTimeMillis(),
)

/** User-defined real-life rewards bought with coins (Habitica style). */
@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey val id: String = newId(),
    val ownerId: String,
    val title: String,
    val cost: Int,
    val icon: String = "CardGiftcard",
    val timesRedeemed: Int = 0,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Optional (DB v6): redeeming also unlocks an app. A package name, [ANY_BLOCKED_APP], or null
     * for a plain real-life treat.
     */
    val unlockPackage: String? = null,
    /** How long the unlock lasts, in minutes (only when [unlockPackage] is set). */
    @ColumnInfo(defaultValue = "0") val unlockMinutes: Int = 0,
) {
    val unlocksApp: Boolean get() = unlockPackage != null && unlockMinutes > 0

    /** Whether this reward can be redeemed to open [pkg] from its pause screen. */
    fun unlocks(pkg: String): Boolean = unlocksApp && (unlockPackage == pkg || unlockPackage == ANY_BLOCKED_APP)

    companion object {
        const val ANY_BLOCKED_APP = "*"
    }
}

/** Cosmetic items owned from the built-in shop catalog. */
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey val itemId: String,
    val acquiredAt: Long = System.currentTimeMillis(),
)

/**
 * Append-only activity log. Every reward/penalty flows through here which gives us
 *  - an audit trail for the stats screen and achievements,
 *  - a ready-made outbox if events are ever shared with a party / challenge server.
 */
@Entity(tableName = "activity_events", indices = [Index("ownerId"), Index("timestamp")])
data class ActivityEventEntity(
    @PrimaryKey val id: String = newId(),
    val ownerId: String,
    val type: String,
    val refId: String? = null,
    val xpDelta: Int = 0,
    val coinDelta: Int = 0,
    val hpDelta: Int = 0,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    /** Null until a sync gateway has acknowledged the event. */
    val syncedAt: Long? = null,
)

/** Equipped cosmetics by slot id. Falls back to the pre-v5 avatarColor/avatarHat/avatarBackdrop columns. */
fun ProfileEntity.equippedMap(): Map<String, String> {
    if (equipped.isNotBlank()) {
        runCatching {
            return kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(equipped)
        }
    }
    return buildMap {
        avatarColor?.let { put("cloak", it) }
        avatarHat?.let { put("hat", it) }
        avatarBackdrop?.let { put("backdrop", it) }
    }
}

fun ProfileEntity.withEquipped(map: Map<String, String>): ProfileEntity =
    copy(equipped = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<Map<String, String>>(), map))

enum class QuestStatus { ACTIVE, COMPLETED, ABANDONED }

/** Progress through a quest line from content.json (one row per line the player has started). */
@Entity(tableName = "quest_progress")
data class QuestProgressEntity(
    @PrimaryKey val lineId: String,
    val stageIndex: Int = 0,
    /** Boss: damage dealt. Collect: items found. Objective: unused (computed from stats). */
    val progress: Int = 0,
    /** Stat value when the stage started, for "gain" objectives. */
    val baseline: Int = 0,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val timesCompleted: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
