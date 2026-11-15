package com.openfocushq.openfocus.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitTimerTest {
    @Test fun countsSteadyFrames() {
        val t = WaitTimer(1_000)
        var now = 0L
        t.onFrame(now) // first frame only sets the baseline
        repeat(30) { now += 16; t.onFrame(now) }
        assertEquals(480, t.elapsedMs)
        assertFalse(t.done)
        assertEquals(1, t.secondsLeft)
    }

    @Test fun gapsWhileHiddenDontCount() {
        val t = WaitTimer(30_000)
        t.onFrame(0)
        t.onFrame(10_000) // screen was covered for 10 s
        assertEquals(250, t.elapsedMs)
    }

    @Test fun finishesAndClamps() {
        val t = WaitTimer(500, initialElapsedMs = 400)
        t.onFrame(0); t.onFrame(200)
        assertTrue(t.done)
        assertEquals(500, t.elapsedMs)
        assertEquals(1f, t.progress)
        assertEquals(0, t.secondsLeft)
    }

    @Test fun clockGoingBackwardsIsIgnored() {
        val t = WaitTimer(1_000)
        t.onFrame(100); t.onFrame(50)
        assertEquals(0, t.elapsedMs)
    }

    @Test fun resumesFromSavedProgress() {
        val t = WaitTimer(30_000, initialElapsedMs = 12_000)
        assertEquals(18, t.secondsLeft)
        assertEquals(0.4f, t.progress, 1e-6f)
    }
}
