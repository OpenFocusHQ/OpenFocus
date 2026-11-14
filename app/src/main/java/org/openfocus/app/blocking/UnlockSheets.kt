package org.openfocus.app.blocking

import org.openfocus.app.core.GameRules
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.data.db.RewardEntity
import org.openfocus.app.data.prefs.Settings

/** Builds the pause screen's bottom sheets. Pure, so prices and choices are unit-tested. */
object UnlockSheets {
    const val FREE = "free:"
    const val PAID = "paid:"
    const val REWARD = "reward:"

    /** After waiting: pick how long, up to the app's free maximum. */
    fun free(s: PauseState) = UnlockSheet(
        title = "How long do you need ${s.appLabel}?",
        options = s.freeDurations.map { UnlockOption("$FREE$it", formatDuration(it), "Free") },
    )

    /** Gift button: every length with its price (longer costs more), then the user's own rewards for this app. */
    fun paid(s: PauseState, coins: Long, settings: Settings, rewards: List<RewardEntity>, pkg: String): UnlockSheet {
        val skip = if (s.gate == BlockGate.WAIT && s.waiting) s.waitMs.toInt() / 1000 else null
        val timed = GameRules.unlockDurations.map { m ->
            val cost = settings.paidUnlockCost(m, skip)
            UnlockOption("$PAID$m", formatDuration(m), "$cost coins", enabled = coins >= cost)
        }
        val own = rewards.filter { it.unlocks(pkg) }.map { r ->
            UnlockOption("$REWARD${r.id}", "${r.title} · ${formatDuration(r.unlockMinutes)}", "${r.cost} coins",
                enabled = coins >= r.cost, section = "Your rewards")
        }
        val notes = listOfNotNull(
            skip?.let { "Includes ${settings.skipCost(it)} coins to skip the ${GameRules.formatWait(it)} wait." },
            if (settings.escalatingPrices) "Each paid unlock today costs more than the last." else null,
            if (own.isNotEmpty()) "Your rewards always cost what you set." else null,
        )
        return UnlockSheet(
            title = if (skip != null) "Skip the wait" else "Unlock with coins",
            subtitle = "You have $coins coins",
            options = timed + own,
            footer = notes.joinToString(" ").ifBlank { null },
        )
    }
}
