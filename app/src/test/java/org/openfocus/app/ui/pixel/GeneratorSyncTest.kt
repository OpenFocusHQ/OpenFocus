package org.openfocus.app.ui.pixel

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Some layout data lives both in the art generator (which bakes it into PNGs) and in Kotlin
 * (which places sprites). These tests read the generator source and fail if the two drift.
 */
class GeneratorSyncTest {
    private val generator: String by lazy {
        listOf("../art/tools/build_placeholders.py", "art/tools/build_placeholders.py")
            .map(::File).first { it.exists() }.readText()
    }

    private fun block(name: String): String {
        val start = generator.indexOf("\n$name = [")
        require(start >= 0) { "$name not found in generator" }
        var depth = 0
        val from = generator.indexOf('[', start)
        for (i in from until generator.length) {
            when (generator[i]) { '[' -> depth++; ']' -> { depth--; if (depth == 0) return generator.substring(from, i + 1) } }
        }
        error("unterminated $name")
    }

    @Test fun badgesAreReadFromContent() {
        // Badge order/tiers aren't duplicated in the generator any more; it reads content.json.
        assertEquals(true, generator.contains("content[\"achievements\"]"))
        assertEquals(SpriteCatalog.BADGE_COLUMNS, Regex("BADGE_COLS = (\\d+)").find(generator)!!.groupValues[1].toInt())
    }

    @Test fun trailMatchesAscentTrail() {
        val pts = Regex("\\((\\d+), (\\d+)\\)").findAll(block("TRAIL")).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()
        assertEquals(AscentTrail.POINTS.map { it.x to it.y }, pts)
    }

    @Test fun emptySceneOrderMatches() {
        val names = Regex("\"(\\w+)\"").findAll(block("EMPTY_SCENES")).map { it.groupValues[1] }.toList()
        val byCol = SpriteCatalog.empty.anims.entries.sortedBy { it.value.col }.map { it.key }
        assertEquals(byCol, names)
    }

    @Test fun idleBobMatchesHeadAnchor() {
        val bob = Regex("IDLE_BOB = \\[([\\d, ]+)]").find(generator)!!.groupValues[1].split(",").map { it.trim().toInt() }
        assertEquals(bob, SpriteCatalog.heroBody.anchors.getValue("head").getValue(SpriteCatalog.IDLE).map { it.y })
    }
}
