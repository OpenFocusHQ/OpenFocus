package com.openfocushq.openfocus.core

import com.openfocushq.openfocus.core.content.GameContent

/** Snapshot of lifetime stats used to evaluate achievements. */
data class GameStats(
    val level: Int = 1,
    val habitCompletions: Int = 0,
    val bestHabitStreak: Int = 0,
    val focusMinutes: Int = 0,
    val focusSessions: Int = 0,
    val goalsCompleted: Int = 0,
    val milestonesDone: Int = 0,
    val temptationsResisted: Int = 0,
    val shortsBlocked: Int = 0,
    val rewardsRedeemed: Int = 0,
    val itemsOwned: Int = 0,
) {
    /** Look up a stat by the name used in content.json. */
    fun value(stat: String): Int = when (stat) {
        "level" -> level
        "habitCompletions" -> habitCompletions
        "bestHabitStreak" -> bestHabitStreak
        "focusMinutes" -> focusMinutes
        "focusSessions" -> focusSessions
        "goalsCompleted" -> goalsCompleted
        "milestonesDone" -> milestonesDone
        "temptationsResisted" -> temptationsResisted
        "shortsBlocked" -> shortsBlocked
        "rewardsRedeemed" -> rewardsRedeemed
        "itemsOwned" -> itemsOwned
        else -> error("Unknown stat '$stat'")
    }

    companion object {
        /** Stat names an achievement may use. Keep in sync with tools/content-editor/content.schema.json (tested). */
        val STATS = listOf(
            "level", "habitCompletions", "bestHabitStreak", "focusMinutes", "focusSessions", "goalsCompleted",
            "milestonesDone", "temptationsResisted", "shortsBlocked", "rewardsRedeemed", "itemsOwned",
        )
    }
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    /** Name of a Material icon, resolved in the UI layer. */
    val icon: String,
    val xpReward: Int,
    val coinReward: Int,
    val stat: String,
    val target: Int,
    val tier: String,
) {
    fun progress(s: GameStats): Pair<Int, Int> = s.value(stat) to target
    fun isMet(s: GameStats): Boolean = s.value(stat) >= target
}

object Achievements {
    /** All achievements, in content.json order (which is also the badge atlas order). */
    val all: List<Achievement> by lazy {
        GameContent.current.achievements.map {
            Achievement(it.id, it.title, it.description, it.icon, it.xp, it.coins, it.stat, it.target, it.tier)
        }
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }

    fun newlyMet(stats: GameStats, unlocked: Set<String>): List<Achievement> =
        all.filter { it.id !in unlocked && it.isMet(stats) }
}
