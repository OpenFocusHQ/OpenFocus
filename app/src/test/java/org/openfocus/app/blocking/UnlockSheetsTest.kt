package org.openfocus.app.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.data.db.RewardEntity
import org.openfocus.app.data.prefs.Settings

class UnlockSheetsTest {
    private val s = PauseState("Feed", BlockReason.ALWAYS, gate = BlockGate.WAIT, waitMs = 60_000, elapsedMs = 10_000, allowMinutes = 10)
    private val prices = Settings(unlockCostPerMinute = 2, skipCostPer10s = 1, escalatingPrices = false)

    @Test fun freeSheetOffersEachLengthUpToTheMaximum() {
        val sheet = UnlockSheets.free(s)
        assertEquals(listOf("free:1", "free:2", "free:5", "free:10"), sheet.options.map { it.key })
        assertTrue(sheet.options.all { it.price == "Free" && it.enabled })
    }

    @Test fun paidRowsShowTheirPriceAndLongerCostsMore() {
        val sheet = UnlockSheets.paid(s, coins = 40, settings = prices, rewards = emptyList(), pkg = "p")
        val byKey = sheet.options.associateBy { it.key }
        assertEquals("8 coins", byKey["paid:1"]!!.price)        // 1 min × 2 + 6 to skip a 60 s wait
        assertEquals("36 coins", byKey["paid:15"]!!.price)
        assertTrue(byKey["paid:15"]!!.enabled)
        assertFalse(byKey["paid:30"]!!.enabled)                  // 66 coins > 40
        assertEquals("Skip the wait", sheet.title)
        assertTrue(sheet.footer!!.contains("6 coins to skip"))
    }

    @Test fun hardBlockHasNoSkipFee() {
        val hard = PauseState("Feed", BlockReason.ALWAYS, gate = BlockGate.HARD)
        val sheet = UnlockSheets.paid(hard, coins = 0, settings = prices, rewards = emptyList(), pkg = "p")
        assertEquals("Unlock with coins", sheet.title)
        assertEquals("10 coins", sheet.options.first { it.key == "paid:5" }.price)
    }

    @Test fun onlyRewardsForThisAppAreListed() {
        val rewards = listOf(
            RewardEntity(id = "a", ownerId = "", title = "Feed time", cost = 20, unlockPackage = "p", unlockMinutes = 30),
            RewardEntity(id = "b", ownerId = "", title = "Any", cost = 99, unlockPackage = RewardEntity.ANY_BLOCKED_APP, unlockMinutes = 60),
            RewardEntity(id = "c", ownerId = "", title = "Other app", cost = 5, unlockPackage = "q", unlockMinutes = 30),
            RewardEntity(id = "d", ownerId = "", title = "Cake", cost = 5),
        )
        val own = UnlockSheets.paid(s, coins = 50, settings = prices, rewards = rewards, pkg = "p").options.filter { it.section != null }
        assertEquals(listOf("reward:a", "reward:b"), own.map { it.key })
        assertEquals(listOf(true, false), own.map { it.enabled })
        assertEquals("Feed time · 30 min", own[0].label)
    }
}
