package org.openfocus.app.core

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
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    /** Name of a Material icon, resolved in the UI layer. */
    val icon: String,
    val xpReward: Int,
    val coinReward: Int,
    val progress: (GameStats) -> Pair<Int, Int>,
) {
    fun isMet(s: GameStats): Boolean = progress(s).let { (cur, goal) -> cur >= goal }
}

object Achievements {
    private fun a(id: String, title: String, desc: String, icon: String, xp: Int, coins: Int, f: (GameStats) -> Pair<Int, Int>) =
        Achievement(id, title, desc, icon, xp, coins, f)

    val all: List<Achievement> = listOf(
        a("first_step", "First Step", "Complete your first habit.", "DirectionsWalk", 20, 5) { it.habitCompletions to 1 },
        a("habit_50", "Creature of Habit", "Complete 50 habits.", "Repeat", 100, 30) { it.habitCompletions to 50 },
        a("habit_500", "Habit Machine", "Complete 500 habits.", "AllInclusive", 400, 120) { it.habitCompletions to 500 },
        a("streak_7", "Week Warrior", "Reach a 7-day streak on any habit.", "LocalFireDepartment", 70, 20) { it.bestHabitStreak to 7 },
        a("streak_30", "Monthly Master", "Reach a 30-day streak.", "Whatshot", 250, 80) { it.bestHabitStreak to 30 },
        a("streak_100", "Centurion", "Reach a 100-day streak.", "MilitaryTech", 800, 250) { it.bestHabitStreak to 100 },
        a("focus_first", "Into the Zone", "Finish your first focus session.", "SelfImprovement", 20, 5) { it.focusSessions to 1 },
        a("focus_10h", "Deep Diver", "Focus for 10 hours in total.", "ScubaDiving", 200, 60) { it.focusMinutes to 600 },
        a("focus_100h", "Flow State", "Focus for 100 hours in total.", "Waves", 1000, 300) { it.focusMinutes to 6000 },
        a("milestone_1", "Base Camp", "Reach your first milestone.", "Flag", 30, 10) { it.milestonesDone to 1 },
        a("goal_1", "Summiteer", "Complete a long-term goal.", "Landscape", 300, 100) { it.goalsCompleted to 1 },
        a("goal_5", "Seven Summits (well, five)", "Complete 5 long-term goals.", "Terrain", 800, 250) { it.goalsCompleted to 5 },
        a("resist_10", "Willpower", "Choose to close a blocked app 10 times.", "Shield", 60, 20) { it.temptationsResisted to 10 },
        a("resist_100", "Iron Will", "Choose to close a blocked app 100 times.", "Security", 300, 100) { it.temptationsResisted to 100 },
        a("shorts_25", "Scroll Dodger", "Have 25 short-video feeds blocked.", "Block", 50, 15) { it.shortsBlocked to 25 },
        a("level_5", "Getting Stronger", "Reach level 5.", "TrendingUp", 0, 50) { it.level to 5 },
        a("level_10", "Seasoned", "Reach level 10.", "Star", 0, 120) { it.level to 10 },
        a("level_25", "Veteran", "Reach level 25.", "AutoAwesome", 0, 400) { it.level to 25 },
        a("treat_yo_self", "Treat Yourself", "Redeem one of your own rewards.", "Cake", 15, 0) { it.rewardsRedeemed to 1 },
        a("collector", "Collector", "Own 5 cosmetic items.", "Checkroom", 80, 0) { it.itemsOwned to 5 },
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    fun newlyMet(stats: GameStats, unlocked: Set<String>): List<Achievement> =
        all.filter { it.id !in unlocked && it.isMet(stats) }
}
