package org.openfocus.app.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openfocus.app.data.db.BlockGate

class PauseStateTest {
    @Test fun waitingCountsDownAndUnlocks() {
        val s = PauseState("Feed", BlockReason.ALWAYS, gate = BlockGate.WAIT, waitMs = 30_000, elapsedMs = 12_000, allowMinutes = 10)
        assertTrue(s.waiting)
        assertEquals(18, s.secondsLeft)
        assertEquals(0.4f, s.progress, 1e-6f)
        assertTrue(s.showCoins) // can still pay to skip
        val done = s.copy(elapsedMs = 30_000)
        assertFalse(done.waiting)
        assertFalse(done.showCoins) // nothing left to pay for
    }

    @Test fun freeLengthsStopAtTheAppsMaximum() {
        assertEquals(listOf(1, 2, 5, 10, 15), PauseState("Feed", BlockReason.ALWAYS, allowMinutes = 15).freeDurations)
    }

    @Test fun hardBlockOffersCoinsUnlessStrictOrShorts() {
        assertTrue(PauseState("Feed", BlockReason.ALWAYS).showCoins)
        assertFalse(PauseState("Feed", BlockReason.FOCUS, canPay = false).showCoins)
        assertFalse(PauseState("TikTok", BlockReason.SHORTS).showCoins)
    }

    @Test fun customQuestionUsesAppPlaceholder() {
        assertEquals("Do you really need Feed right now?", PauseState("Feed", BlockReason.ALWAYS).headline)
        assertEquals("Is Feed worth it?", PauseState("Feed", BlockReason.ALWAYS, question = "Is {app} worth it?").headline)
        assertEquals("YouTube Shorts blocked", PauseState("YouTube", BlockReason.SHORTS, "YouTube Shorts", question = "x").headline)
    }

    /** Ascent-style: say at most one thing under the question. */
    @Test fun atMostOneQuietLine() {
        assertNull(PauseState("Feed", BlockReason.ALWAYS).subline())
        assertEquals("Scheduled rest: Night.", PauseState("Feed", BlockReason.SCHEDULE, "Night").subline())
        assertEquals("Mind the marathon", PauseState("Feed", BlockReason.SCHEDULE, "Night", reminder = "Mind the marathon").subline())
        assertEquals("You're in a focus session on \"Essay\" · about 5 min left.",
            PauseState("Feed", BlockReason.FOCUS, "Essay", focusEndsAt = 4 * 60_000L + 1).subline(now = 0))
        assertEquals("The rest of Instagram still works.", PauseState("Instagram", BlockReason.SHORTS, "Reels", backToApp = true).subline())
        assertEquals("TikTok is all short videos.", PauseState("TikTok", BlockReason.SHORTS, "TikTok").subline())
    }
}
