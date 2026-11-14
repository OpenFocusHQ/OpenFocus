package org.openfocus.app.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseStyleTest {
    @Test fun unknownIdsFallBackToDefaults() {
        assertEquals(PauseIcon.SHIELD, PauseIcon.of("nope"))
        assertEquals(PauseColors.SYSTEM, PauseColors.of(null))
    }

    @Test fun idsAreUniqueAndStable() {
        assertEquals(PauseIcon.entries.size, PauseIcon.entries.map { it.id }.toSet().size)
        assertEquals(PauseColors.entries.size, PauseColors.entries.map { it.id }.toSet().size)
        assertEquals("shield", PauseIcon.SHIELD.id)  // stored in settings: don't rename
        assertEquals("system", PauseColors.SYSTEM.id)
    }

    /** Text must stay readable on every colour pair (WCAG contrast ≥ 4.5). */
    @Test fun everyColourPairIsReadable() {
        fun lum(c: Long): Double {
            fun ch(v: Long): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch(c shr 16 and 0xFF) + 0.7152 * ch(c shr 8 and 0xFF) + 0.0722 * ch(c and 0xFF)
        }
        fun contrast(a: Long, b: Long): Double { val (x, y) = lum(a) to lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }
        PauseColors.entries.filter { it != PauseColors.SYSTEM }.forEach {
            assertTrue("${it.id}: ${contrast(it.background, it.foreground)}", contrast(it.background, it.foreground) >= 4.5)
        }
        assertTrue(contrast(PauseColors.LIGHT_BG, PauseColors.LIGHT_FG) >= 4.5)
        assertTrue(contrast(PauseColors.DARK_BG, PauseColors.DARK_FG) >= 4.5)
    }
}
