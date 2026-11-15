package com.openfocushq.openfocus.core

import kotlin.random.Random
import com.openfocushq.openfocus.core.content.GameContent

/** Encouragement lines come from content.json (CC0); streak messages stay here as they're formulas. */
object Encouragement {
    private val m get() = GameContent.current.messages

    fun general(seed: Long = System.currentTimeMillis() / 3_600_000) = m.general[Random(seed).nextInt(m.general.size)]
    fun resist() = m.resist.random()
    fun blocked() = m.blocked.random()
    fun shorts() = m.shorts.random()
    fun comeback() = m.comeback.random()

    fun afterHabit(streak: Int): String = when {
        streak >= 100 -> "$streak days. That's not a streak, that's who you are."
        streak >= 30 -> "$streak-day streak! Unstoppable."
        streak >= 7 -> "$streak in a row — a whole week of wins!"
        streak >= 3 -> "$streak-day streak. Keep the fire going!"
        else -> "Done! Every rep counts."
    }
}
