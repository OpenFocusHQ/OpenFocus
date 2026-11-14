package org.openfocus.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openfocus.app.data.db.Difficulty

class GameRulesTest {
    @Test fun levelCurveIsConsistent() {
        assertEquals(1, GameRules.levelFor(0))
        assertEquals(1, GameRules.levelFor(99))
        assertEquals(2, GameRules.levelFor(100))
        assertEquals(3, GameRules.levelFor(300))
        assertEquals(4, GameRules.levelFor(600))
        for (l in 1..80) {
            assertEquals(l, GameRules.levelFor(GameRules.xpForLevel(l)))
            assertEquals(l, GameRules.levelFor(GameRules.xpForLevel(l + 1) - 1))
        }
    }

    @Test fun progressFractionWithinLevel() {
        val p = GameRules.progress(150)
        assertEquals(2, p.level)
        assertEquals(50, p.intoLevel)
        assertEquals(200, p.levelSpan)
    }

    @Test fun streakBonusIsCapped() {
        val base = GameRules.habitXp(Difficulty.EASY, 0)
        assertEquals(10, base)
        assertEquals(GameRules.habitXp(Difficulty.EASY, 10), GameRules.habitXp(Difficulty.EASY, 500))
        assertTrue(GameRules.habitXp(Difficulty.HARD, 0) > GameRules.habitXp(Difficulty.MEDIUM, 0))
    }

    @Test fun deepFocusEarnsMore() {
        assertTrue(GameRules.focusXp(25, deep = true) > GameRules.focusXp(25, deep = false))
        assertEquals(50, GameRules.focusXp(25, false))
    }

    @Test fun unlocksGetPricier() {
        assertTrue(GameRules.unlockCost(5, 1) > GameRules.unlockCost(5, 0))
    }

    @Test fun skippingAWaitCostsMoreForLongerWaitsAndRepeatUnlocks() {
        assertEquals(3, GameRules.skipWaitCost(10, 0))   // minimum price
        assertEquals(6, GameRules.skipWaitCost(60, 0))
        assertTrue(GameRules.skipWaitCost(300, 0) > GameRules.skipWaitCost(60, 0))
        assertEquals(2 * GameRules.skipWaitCost(60, 0), GameRules.skipWaitCost(60, 1))
    }

    @Test fun pricesAreConfigurable() {
        assertEquals(0, GameRules.skipWaitCost(60, 3, per10s = 0)) // free skipping
        assertEquals(18, GameRules.skipWaitCost(60, 0, per10s = 3))
        assertEquals(15, GameRules.unlockCost(5, 0, perMinute = 3))
        assertEquals(0, GameRules.unlockCost(15, 2, perMinute = 0))
        assertEquals("1 min 30s", GameRules.formatWait(90))
        assertEquals("45s", GameRules.formatWait(45))
        assertEquals("5 min", GameRules.formatWait(300))
    }

    @Test fun maxHpCapped() {
        assertEquals(GameRules.MAX_MAX_HP, GameRules.maxHpFor(1000))
    }

    @Test fun achievementsHaveUniqueIds() {
        val ids = Achievements.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Achievements.newlyMet(GameStats(habitCompletions = 1), emptySet()).any { it.id == "first_step" })
        assertTrue(Achievements.newlyMet(GameStats(habitCompletions = 1), setOf("first_step")).none { it.id == "first_step" })
    }
}
