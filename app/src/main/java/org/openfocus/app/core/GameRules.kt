package org.openfocus.app.core

import org.openfocus.app.data.db.Difficulty
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * All the numbers that drive the game live here so they are easy to tune and test.
 * The general philosophy: reward consistency generously, punish lapses gently.
 */
object GameRules {
    const val BASE_HABIT_XP = 10
    const val MAX_STREAK_BONUS_STEPS = 10
    const val STREAK_BONUS_PER_STEP = 0.05

    const val FOCUS_XP_PER_MINUTE = 2
    const val DEEP_FOCUS_BONUS = 0.25
    const val FOCUS_ABANDON_DAMAGE = 5

    const val MILESTONE_XP = 50
    const val MILESTONE_COINS = 15
    const val GOAL_XP = 300
    const val GOAL_COINS = 100

    const val RESIST_XP = 3
    const val RESIST_COINS = 1

    const val BASE_MAX_HP = 50
    const val MAX_MAX_HP = 100

    fun habitXp(difficulty: Difficulty, streak: Int): Int {
        val bonus = 1 + streak.coerceIn(0, MAX_STREAK_BONUS_STEPS) * STREAK_BONUS_PER_STEP
        return (BASE_HABIT_XP * difficulty.multiplier * bonus).roundToInt().coerceAtLeast(1)
    }

    fun habitCoins(xp: Int): Int = (xp / 4).coerceAtLeast(1)

    /** HP lost when a "break" habit slips (e.g. "doomscrolled"). */
    fun slipDamage(difficulty: Difficulty): Int = (4 * difficulty.multiplier).roundToInt().coerceAtLeast(1)

    /** HP lost for every scheduled habit missed at day rollover. */
    fun missDamage(difficulty: Difficulty): Int = (3 * difficulty.multiplier).roundToInt().coerceAtLeast(1)

    fun focusXp(minutes: Int, deep: Boolean): Int {
        val base = minutes * FOCUS_XP_PER_MINUTE
        return if (deep) (base * (1 + DEEP_FOCUS_BONUS)).roundToInt() else base
    }

    fun focusCoins(minutes: Int): Int = minutes / 5 + if (minutes >= 25) 2 else 0

    /** Total XP needed to *reach* [level] (level 1 = 0 XP, 2 = 100, 3 = 300, 4 = 600 ...). */
    fun xpForLevel(level: Int): Long = 50L * level * (level - 1)

    fun levelFor(totalXp: Long): Int {
        // Inverse of 50 L (L-1) = xp  ->  L = (1 + sqrt(1 + xp/12.5)) / 2
        var l = floor((1 + sqrt(1 + totalXp / 12.5)) / 2).toInt().coerceAtLeast(1)
        while (xpForLevel(l + 1) <= totalXp) l++
        while (l > 1 && xpForLevel(l) > totalXp) l--
        return l
    }

    data class LevelProgress(val level: Int, val intoLevel: Long, val levelSpan: Long) {
        val fraction: Float get() = if (levelSpan == 0L) 0f else intoLevel.toFloat() / levelSpan
    }

    fun progress(totalXp: Long): LevelProgress {
        val l = levelFor(totalXp)
        val start = xpForLevel(l)
        return LevelProgress(l, totalXp - start, xpForLevel(l + 1) - start)
    }

    fun maxHpFor(level: Int): Int = (BASE_MAX_HP + (level - 1) * 2).coerceAtMost(MAX_MAX_HP)

    /** Coins lost when HP hits zero ("you needed a rest"). */
    fun faintCoinPenalty(coins: Long): Long = (coins / 10)

    /** Price of a temporary unlock from the block screen, scaling with how often you've used it today. */
    const val DEFAULT_UNLOCK_COST_PER_MINUTE = 2
    const val DEFAULT_SKIP_COST_PER_10S = 1
    /** Bounds for user-configured prices (Settings → Pause screen). */
    val priceRange = 0..20

    fun unlockCost(minutes: Int, unlocksToday: Int, perMinute: Int = DEFAULT_UNLOCK_COST_PER_MINUTE): Int =
        (minutes * perMinute) * (1 + unlocksToday)

    val unlockOptions = listOf(5, 15)

    /** Slider stops when configuring a WAIT gate (seconds) and the open time afterwards (minutes). */
    val waitStepsSeconds = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600)
    val allowStepsMinutes = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)

    /**
     * Coins to skip a wait timer: [per10s] coins per started 10 s of waiting (min 3 × per10s),
     * escalating with each paid unlock today, so skipping is possible but never mindless.
     * A price of 0 makes skipping free.
     */
    fun skipWaitCost(waitSeconds: Int, unlocksToday: Int, per10s: Int = DEFAULT_SKIP_COST_PER_10S): Int =
        maxOf(3, (waitSeconds + 9) / 10) * per10s * (1 + unlocksToday)

    fun formatWait(seconds: Int): String = when {
        seconds < 60 -> "${seconds}s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60} min ${seconds % 60}s"
    }

    /** Titles shown under the hero's name. */
    fun titleFor(level: Int): String = when {
        level >= 40 -> "Summit Legend"
        level >= 30 -> "Mountain Sage"
        level >= 20 -> "Peak Seeker"
        level >= 12 -> "Ridge Walker"
        level >= 6 -> "Trailblazer"
        level >= 3 -> "Wanderer"
        else -> "Novice Climber"
    }
}
