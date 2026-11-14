package org.openfocus.app.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.data.db.BlockMode
import org.openfocus.app.data.db.BlockRuleEntity
import org.openfocus.app.data.db.BlockScheduleEntity
import org.openfocus.app.data.prefs.ActiveFocus
import org.openfocus.app.data.prefs.Settings
import java.time.LocalDate

class BlockPolicyTest {
    private val monday = LocalDate.of(2026, 11, 9).toEpochDay()
    private val now = 1_000_000L
    private val app = "com.example.feed"

    private fun decide(config: BlockConfig, minute: Int = 12 * 60, day: Long = monday, usage: Int = 0, exempt: Set<String> = emptySet()) =
        BlockPolicy.decide(app, config, now, day, minute, exempt) { usage }

    private fun rule(mode: BlockMode, limit: Int = 30, gate: BlockGate = BlockGate.HARD, wait: Int = 30, allow: Int = 10) =
        mapOf(app to BlockRuleEntity(app, "Feed", mode, limit, gate = gate, waitSeconds = wait, allowMinutes = allow))

    @Test fun unlistedAppNotBlocked() = assertNull(decide(BlockConfig()))

    @Test fun alwaysModeBlocks() = assertEquals(BlockReason.ALWAYS, decide(BlockConfig(rule(BlockMode.ALWAYS)))?.reason)

    @Test fun exemptWins() = assertNull(decide(BlockConfig(rule(BlockMode.ALWAYS)), exempt = setOf(app)))

    @Test fun dailyLimit() {
        val c = BlockConfig(rule(BlockMode.DAILY_LIMIT, 30))
        assertNull(decide(c, usage = 29))
        assertEquals(BlockReason.DAILY_LIMIT, decide(c, usage = 30)?.reason)
    }

    @Test fun focusBlocksListedApps() {
        val focus = ActiveFocus("f", 0, now + 60_000, 25, "Essay", null, deep = false)
        val c = BlockConfig(rule(BlockMode.FOCUS_AND_SCHEDULE), settings = Settings(activeFocus = focus))
        assertEquals(BlockReason.FOCUS, decide(c)?.reason)
        // Expired session no longer blocks
        val expired = c.copy(settings = Settings(activeFocus = focus.copy(endsAt = now - 1)))
        assertNull(decide(expired))
    }

    @Test fun deepFocusBlocksEverythingButAllowlist() {
        val focus = ActiveFocus("f", 0, now + 60_000, 25, "", null, deep = true)
        assertEquals(BlockReason.DEEP_FOCUS, decide(BlockConfig(settings = Settings(activeFocus = focus)))?.reason)
        assertNull(decide(BlockConfig(settings = Settings(activeFocus = focus, focusAllowlist = setOf(app)))))
    }

    @Test fun unlockPassHonouredUnlessStrictDuringFocus() {
        val pass = mapOf(app to now + 60_000)
        assertNull(decide(BlockConfig(rule(BlockMode.ALWAYS), settings = Settings(unlockPasses = pass))))
        val focus = ActiveFocus("f", 0, now + 60_000, 25, "", null, deep = false)
        val strict = Settings(unlockPasses = pass, activeFocus = focus, strictMode = true)
        assertEquals(BlockReason.FOCUS, decide(BlockConfig(rule(BlockMode.ALWAYS), settings = strict))?.reason)
    }

    @Test fun pausedShieldStillBlocksDuringFocus() {
        assertNull(decide(BlockConfig(rule(BlockMode.ALWAYS), settings = Settings(shieldPaused = true))))
        val focus = ActiveFocus("f", 0, now + 60_000, 25, "", null, deep = false)
        assertEquals(BlockReason.FOCUS, decide(BlockConfig(rule(BlockMode.ALWAYS), settings = Settings(shieldPaused = true, activeFocus = focus)))?.reason)
    }

    @Test fun ruleGateIsPassedThrough() {
        val wait = decide(BlockConfig(rule(BlockMode.ALWAYS, gate = BlockGate.WAIT, wait = 60, allow = 15)))!!
        assertEquals(BlockGate.WAIT, wait.gate)
        assertEquals(60, wait.waitSeconds)
        assertEquals(15, wait.allowMinutes)
        assertTrue(wait.canPay)
        val hard = decide(BlockConfig(rule(BlockMode.ALWAYS, gate = BlockGate.HARD)))!!
        assertEquals(BlockGate.HARD, hard.gate)
        assertEquals(0, hard.waitSeconds)
    }

    @Test fun focusFollowsRuleGateUnlessStrict() {
        val focus = ActiveFocus("f", 0, now + 60_000, 25, "Essay", null, deep = false)
        val r = rule(BlockMode.FOCUS_AND_SCHEDULE, gate = BlockGate.WAIT)
        val relaxed = decide(BlockConfig(r, settings = Settings(activeFocus = focus)))!!
        assertEquals(BlockGate.WAIT, relaxed.gate)
        val strict = decide(BlockConfig(r, settings = Settings(activeFocus = focus, strictMode = true)))!!
        assertEquals(BlockGate.HARD, strict.gate)
        assertFalse(strict.canPay)
    }

    @Test fun deepFocusIsHardWithPaidEscapeUnlessStrict() {
        val focus = ActiveFocus("f", 0, now + 60_000, 25, "", null, deep = true)
        val d = decide(BlockConfig(settings = Settings(activeFocus = focus)))!!
        assertEquals(BlockGate.HARD, d.gate)
        assertTrue(d.canPay)
        assertFalse(decide(BlockConfig(settings = Settings(activeFocus = focus, strictMode = true)))!!.canPay)
    }

    @Test fun waitedPassLetsTheAppThroughUntilItExpires() {
        val r = rule(BlockMode.ALWAYS, gate = BlockGate.WAIT)
        assertNull(decide(BlockConfig(r, settings = Settings(unlockPasses = mapOf(app to now + 1)))))
        assertEquals(BlockReason.ALWAYS, decide(BlockConfig(r, settings = Settings(unlockPasses = mapOf(app to now))))?.reason)
    }

    @Test fun overnightSchedule() {
        // Monday-only 22:00 -> 07:00
        val s = BlockScheduleEntity(name = "Night", startMinute = 22 * 60, endMinute = 7 * 60, daysMask = 0b0000001)
        assertTrue(BlockPolicy.isScheduleActive(s, monday, 23 * 60))
        assertTrue(BlockPolicy.isScheduleActive(s, monday + 1, 6 * 60)) // Tuesday early morning belongs to Monday
        assertFalse(BlockPolicy.isScheduleActive(s, monday, 6 * 60))     // Monday early morning belongs to Sunday
        assertFalse(BlockPolicy.isScheduleActive(s, monday + 1, 23 * 60))
        val c = BlockConfig(rule(BlockMode.FOCUS_AND_SCHEDULE), listOf(s))
        assertEquals(BlockReason.SCHEDULE, decide(c, minute = 23 * 60)?.reason)
        assertNull(decide(c, minute = 12 * 60))
    }
}
