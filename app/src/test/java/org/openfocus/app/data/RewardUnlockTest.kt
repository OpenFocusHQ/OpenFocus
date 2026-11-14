package org.openfocus.app.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.OpenFocusApp
import org.openfocus.app.data.db.BlockRuleEntity
import org.openfocus.app.data.db.RewardEntity
import org.robolectric.RobolectricTestRunner

/** Self-made rewards that optionally unlock apps (Hero → Rewards, and the pause screen's coin dialog). */
@RunWith(RobolectricTestRunner::class)
class RewardUnlockTest {
    private val c = ApplicationProvider.getApplicationContext<OpenFocusApp>().container
    private val feed = "com.example.feed"
    private val video = "com.example.video"

    private fun setUp(coins: Int) = runBlocking {
        c.game.apply("test", coins = coins)
        c.blocks.upsertRule(BlockRuleEntity(feed, "Feed"))
        c.blocks.upsertRule(BlockRuleEntity(video, "Video"))
    }

    private fun reward(title: String, cost: Int, pkg: String?, minutes: Int) = runBlocking {
        val r = RewardEntity(ownerId = "", title = title, cost = cost, unlockPackage = pkg, unlockMinutes = minutes)
        c.shop.saveReward(r); r
    }

    private fun coins() = runBlocking { c.game.profile().coins }
    private fun passMinutes(pkg: String): Long =
        ((c.settings.value.unlockPasses[pkg] ?: 0L) - System.currentTimeMillis() + 30_000) / 60_000

    @Test fun linkedRewardUnlocksItsAppFromThePauseScreen() {
        setUp(100)
        val r = reward("Evening feed", 40, feed, 30)
        assertTrue(r.unlocks(feed))
        assertTrue(runBlocking { c.shop.redeem(r.id, forPackage = feed) })
        assertEquals(60, coins())
        assertEquals(30, passMinutes(feed))
        assertEquals(0, c.settings.value.unlocksTodayEffective()) // fixed price: doesn't escalate paid unlocks
        assertEquals(1, runBlocking { c.db.gameDao().reward(r.id) }!!.timesRedeemed)
    }

    @Test fun wrongAppIsRefusedWithoutCharging() {
        setUp(100)
        val r = reward("Evening feed", 40, feed, 30)
        assertFalse(runBlocking { c.shop.redeem(r.id, forPackage = video) })
        assertEquals(100, coins())
        assertFalse(video in c.settings.value.unlockPasses)
    }

    @Test fun anyAppRewardFromTheRewardsTabUnlocksEveryBlockedApp() {
        setUp(100)
        val r = reward("Free time", 25, RewardEntity.ANY_BLOCKED_APP, 15)
        assertTrue(r.unlocks(feed) && r.unlocks(video))
        assertTrue(runBlocking { c.shop.redeem(r.id) })
        assertEquals(15, passMinutes(feed))
        assertEquals(15, passMinutes(video))
    }

    @Test fun plainTreatsUnlockNothing() {
        setUp(100)
        val r = reward("Cake", 30, null, 0)
        assertFalse(r.unlocks(feed))
        assertTrue(runBlocking { c.shop.redeem(r.id) })
        assertTrue(c.settings.value.unlockPasses.isEmpty())
        assertFalse(runBlocking { c.shop.redeem(r.id, forPackage = feed) }) // not usable from a pause screen
    }

    @Test fun cantRedeemWithoutEnoughCoins() {
        setUp(10)
        val r = reward("Evening feed", 40, feed, 30)
        assertFalse(runBlocking { c.shop.redeem(r.id, forPackage = feed) })
        assertEquals(10, coins())
        assertTrue(c.blocks.rules.let { runBlocking { it.first() } }.isNotEmpty())
    }
}
