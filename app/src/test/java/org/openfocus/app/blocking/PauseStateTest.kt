package org.openfocus.app.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun hardBlockOffersCoinsUnlessStrictOrShorts() {
        assertTrue(PauseState("Feed", BlockReason.ALWAYS).showCoins)
        assertFalse(PauseState("Feed", BlockReason.FOCUS, canPay = false).showCoins)
        assertFalse(PauseState("TikTok", BlockReason.SHORTS).showCoins)
    }

    @Test fun customQuestionUsesAppPlaceholder() {
        assertEquals("Do you really need Feed right now?", PauseState("Feed", BlockReason.ALWAYS).headline)
        assertEquals("Is Feed worth it?", PauseState("Feed", BlockReason.ALWAYS, question = "Is {app} worth it?").headline)
        assertEquals("Breathe.", PauseState("Feed", BlockReason.ALWAYS, question = "Breathe.").headline)
        // Shorts keep their own headline
        assertEquals("YouTube Shorts blocked", PauseState("YouTube", BlockReason.SHORTS, "YouTube Shorts", question = "x").headline)
    }

    @Test fun contextLines() {
        assertEquals("Scheduled rest: Night.", PauseState("Feed", BlockReason.SCHEDULE, "Night").contextLine())
        assertEquals("You're in a focus session on \"Essay\" · about 5 min left.",
            PauseState("Feed", BlockReason.FOCUS, "Essay", focusEndsAt = 4 * 60_000L + 1).contextLine(now = 0))
    }
}
