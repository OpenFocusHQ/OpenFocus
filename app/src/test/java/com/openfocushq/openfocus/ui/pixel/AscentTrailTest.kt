package com.openfocushq.openfocus.ui.pixel

import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AscentTrailTest {
    @Test fun endpoints() {
        assertEquals(AscentTrail.POINTS.first(), AscentTrail.pointAt(0f))
        assertEquals(AscentTrail.POINTS.last(), AscentTrail.pointAt(1f))
        assertEquals(AscentTrail.POINTS.last(), AscentTrail.pointAt(5f)) // clamped
    }

    @Test fun climbingGoesUp() {
        // Overall the trail climbs: later points are never meaningfully lower than earlier ones.
        val ys = (0..100).map { AscentTrail.pointAt(it / 100f).y }
        ys.zipWithNext().forEach { (a, b) -> assertTrue("trail descends: $a -> $b", b <= a + 1) }
    }

    @Test fun pointsStayInsideScene() {
        (0..100).map { AscentTrail.pointAt(it / 100f) }.forEach { p ->
            assertTrue(p.x in 0 until 160 && p.y in 0 until 112)
            // Props drawn around the point must stay inside the scene too.
            val climberTopLeft = p - IntOffset(5 + AscentTrail.CLIMBER_BESIDE_CAMP, 11)
            assertTrue(climberTopLeft.x >= 0 && climberTopLeft.y >= 0 && climberTopLeft.x + 10 <= 160)
        }
    }

    @Test fun climberSitsOnCamps() {
        val n = 4
        for (done in 0..n) {
            val f = AscentTrail.climberFraction(done, n, completed = false)
            if (done > 0) assertEquals(AscentTrail.campFraction(done - 1, n), f, 1e-6f)
            assertTrue(f < 1f)
        }
        assertEquals(1f, AscentTrail.climberFraction(2, n, completed = true))
        assertEquals(0f, AscentTrail.climberFraction(0, 0, completed = false))
    }
}
