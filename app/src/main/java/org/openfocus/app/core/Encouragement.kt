package org.openfocus.app.core

import kotlin.random.Random

/** Original encouragement lines (CC0 — reuse freely). */
object Encouragement {
    private val general = listOf(
        "Small steps still climb mountains.",
        "You don't need a perfect day, just a next step.",
        "Attention is your most valuable currency. Spend it on purpose.",
        "Every time you choose focus, future-you gets a little stronger.",
        "Consistency beats intensity. Show up again today.",
        "The feed will still be there. Your goals won't wait forever.",
        "Progress, not perfection.",
        "One focused hour can change the shape of a week.",
        "You are building the person who finishes things.",
        "Boredom is where good ideas come from. Let it in.",
    )

    private val resist = listOf(
        "Nice save! That's a point for future-you.",
        "Closed it. That's what discipline feels like.",
        "You just won a tiny battle. They add up.",
        "Back to your quest, adventurer.",
    )

    private val blocked = listOf(
        "This app is resting right now. What were you about to do instead?",
        "Pause. Breathe. Is this what you opened your phone for?",
        "Your attention is on a quest. This can wait.",
        "Shield up! Take three slow breaths before deciding.",
    )

    private val shorts = listOf(
        "Short videos are built to be endless. You're not.",
        "Infinite scroll detected and dodged.",
        "Swipe-feeds blocked. The rest of the app is still yours.",
    )

    private val comeback = listOf(
        "A missed day is just a plot twist. Back on the trail!",
        "Heroes fall. Legends get back up.",
        "Rest restored you. Let's start fresh.",
    )

    fun general(seed: Long = System.currentTimeMillis() / 3_600_000) = general[Random(seed).nextInt(general.size)]
    fun resist() = resist.random()
    fun blocked() = blocked.random()
    fun shorts() = shorts.random()
    fun comeback() = comeback.random()

    fun afterHabit(streak: Int): String = when {
        streak >= 100 -> "$streak days. That's not a streak, that's who you are."
        streak >= 30 -> "$streak-day streak! Unstoppable."
        streak >= 7 -> "$streak in a row — a whole week of wins!"
        streak >= 3 -> "$streak-day streak. Keep the fire going!"
        else -> "Done! Every rep counts."
    }
}
