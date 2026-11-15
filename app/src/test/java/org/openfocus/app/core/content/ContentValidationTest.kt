package org.openfocus.app.core.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openfocus.app.core.GameStats
import org.openfocus.app.data.db.Difficulty
import org.openfocus.app.data.db.HabitFrequency
import org.openfocus.app.data.db.HabitKind
import org.openfocus.app.ui.components.AppIcons
import org.openfocus.app.ui.pixel.SkinTone
import org.openfocus.app.ui.pixel.SpriteRegistry
import java.io.File

/**
 * Guards content.json (edited with tools/content-editor): the app trusts it at runtime, so CI
 * rejects anything inconsistent before it can ship.
 */
class ContentValidationTest {
    private val c = GameContent.current
    private fun repo(path: String) = listOf(File("../$path"), File(path)).first { it.exists() }
    private val schema: JsonObject by lazy { Json.parseToJsonElement(repo("tools/content-editor/content.schema.json").readText()).jsonObject }

    private fun enumOf(path: String): List<String> {
        var o: JsonObject = schema
        path.split("/").forEach { o = o[it]!!.jsonObject }
        return (o["enum"] as JsonArray).filter { it !is JsonNull }.map { it.jsonPrimitive.content }
    }

    private fun <T> assertUnique(what: String, ids: List<T>) {
        val dupes = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("duplicate $what: $dupes", dupes.isEmpty())
    }

    @Test fun idsAreUnique() {
        assertUnique("slot ids", c.slots.map { it.id })
        assertUnique("slot layers", c.slots.map { it.layer } + 1) // 1 = the hero's body
        assertUnique("item ids", c.items.map { it.id })
        assertUnique("achievement ids", c.achievements.map { it.id })
        assertUnique("starter habit ids", c.starterHabits.map { it.id })
        assertUnique("title levels", c.titles.map { it.minLevel })
    }

    @Test fun slotsAreConsistent() {
        assertEquals("exactly one backdrop slot", 1, c.slots.count { it.kind == SlotKind.BACKDROP })
        c.slots.filter { it.kind == SlotKind.OVERLAY }.forEach { assertTrue("${it.id} needs an anchor", it.anchor in listOf("head", "body")) }
        c.slots.filter { it.required }.forEach { s ->
            val d = c.item(s.defaultItem)
            assertTrue("required slot ${s.id} needs a defaultItem in that slot", d != null && d.slot == s.id)
            assertEquals("default ${d!!.id} must be free", 0, d.price)
        }
    }

    @Test fun itemsAreConsistent() {
        c.items.forEach { i ->
            assertTrue("${i.id}: unknown slot '${i.slot}'", c.slot(i.slot) != null)
            assertTrue("${i.id}: bad colour ${i.color}", Regex("^#[0-9A-Fa-f]{6}$").matches(i.color))
            i.sprite?.let { assertTrue("${i.id}: sprite '$it' not in SpriteRegistry (run the generator)", it in SpriteRegistry.sprites) }
            assertTrue("${i.id}: price >= 0", i.price >= 0)
        }
        c.slots.mapNotNull { it.defaultSprite }.forEach { assertTrue("default sprite $it missing", it in SpriteRegistry.sprites) }
    }

    @Test fun achievementsAreConsistent() {
        c.achievements.forEach { a ->
            assertTrue("${a.id}: unknown stat '${a.stat}'", a.stat in GameStats.STATS)
            assertTrue("${a.id}: unknown tier '${a.tier}'", a.tier in listOf("bronze", "silver", "gold"))
            assertTrue("${a.id}: target must be positive", a.target > 0)
        }
    }

    @Test fun starterHabitsTitlesAndMessages() {
        c.starterHabits.forEach { q ->
            assertTrue("${q.id}: unknown icon '${q.icon}'", q.icon in AppIcons.habitChoices.keys)
            assertTrue("${q.id}: colour index 0..7", q.color in 0..7)
        }
        assertTrue("some starter habits", c.starterHabits.any { it.starter })
        assertTrue("a title for level 1", c.titles.any { it.minLevel == 1 })
        with(c.messages) { listOf(general, resist, blocked, shorts, comeback).forEach { assertTrue(it.isNotEmpty()) } }
    }

    @Test fun charactersAreConsistent() {
        assertUnique("npc ids", c.npcs.map { it.id })
        assertUnique("enemy ids", c.enemies.map { it.id })
        val colour = Regex("^#[0-9A-Fa-f]{6}$")
        c.npcs.forEach { n ->
            assertTrue("${n.id}: bad colour", colour.matches(n.color))
            n.hat?.let { assertTrue("${n.id}: hat '$it' is not a head item", c.item(it)?.slot == "hat") }
            n.sprite?.let { assertTrue("${n.id}: sprite '$it' missing (run the generator)", it in SpriteRegistry.sprites) }
        }
        c.enemies.forEach { e ->
            assertTrue("${e.id}: hp must be positive", e.hp > 0)
            assertTrue("${e.id}: bad colour", colour.matches(e.color))
            e.sprite?.let { assertTrue("${e.id}: sprite '$it' missing (run the generator)", it in SpriteRegistry.sprites) }
        }
    }

    @Test fun questLinesAreConsistent() {
        assertUnique("quest line ids", c.questLines.map { it.id })
        val speakers = c.npcs.map { it.id } + c.enemies.map { it.id } + "hero"
        c.questLines.forEach { q ->
            assertTrue("${q.id}: unknown npc '${q.npc}'", c.npc(q.npc) != null)
            assertTrue("${q.id}: needs stages", q.stages.isNotEmpty())
            assertTrue("${q.id}: unlockLevel >= 1", q.unlockLevel >= 1)
            assertTrue("${q.id}: cost >= 0", q.cost >= 0)
            q.requires?.let { assertTrue("${q.id}: requires unknown line '$it'", c.questLine(it) != null && it != q.id) }
            assertUnique("${q.id} stage ids", q.stages.map { it.id })
            q.stages.forEach { s ->
                val where = "${q.id}/${s.id}"
                val wins = listOfNotNull(s.objective, s.boss, s.collect).size
                assertEquals("$where: exactly one of objective / boss / collect", 1, wins)
                s.objective?.let {
                    assertTrue("$where: unknown stat '${it.stat}'", it.stat in GameStats.STATS)
                    assertTrue("$where: amount must be positive", it.amount > 0)
                }
                s.boss?.let { assertTrue("$where: unknown enemy '$it'", c.enemy(it) != null) }
                s.collect?.let {
                    assertTrue("$where: amount must be positive", it.amount > 0)
                    assertTrue("$where: unknown icon '${it.icon}'", it.icon in AppIcons.names)
                }
                s.reward.item?.let { assertTrue("$where: unknown reward item '$it'", c.item(it) != null) }
                (s.intro + s.outro).forEach { l -> assertTrue("$where: unknown speaker '${l.speaker}'", l.speaker in speakers) }
            }
        }
        // No cycles in `requires`, or a line could never unlock.
        c.questLines.forEach { q ->
            val seen = mutableSetOf(q.id)
            var next = q.requires
            while (next != null) {
                assertTrue("${q.id}: requires cycle through '$next'", seen.add(next))
                next = c.questLine(next)?.requires
            }
        }
    }

    /** Every stat a quest objective can use has wording in strings_quests.xml. */
    @Test fun objectiveWordingCoversEveryStat() {
        assertEquals(GameStats.STATS.toSet(), org.openfocus.app.ui.quests.OBJECTIVE_GAIN.keys)
        assertEquals(GameStats.STATS.toSet(), org.openfocus.app.ui.quests.OBJECTIVE_REACH.keys)
    }

    /** Translation overlays may only target text that exists, and must keep {hero} placeholders. */
    @Test fun translationsMatchTheSource() {
        val source = ContentI18n.keys(Json.parseToJsonElement(repo("app/src/main/resources/content/content.json").readText()))
        val dir = repo("app/src/main/resources/content/i18n")
        dir.listFiles { f -> f.extension == "json" }.orEmpty().forEach { f ->
            val overlay = ContentI18n.parseOverlay(f.readText())
            overlay.forEach { (k, v) ->
                val en = source[k]
                assertTrue("${f.name}: unknown key '$k' (renamed or removed?)", en != null)
                if ("{hero}" in en!!) assertTrue("${f.name}: '$k' lost the {hero} placeholder", "{hero}" in v || v.isBlank())
            }
            GameContent.forLocale(java.util.Locale.forLanguageTag(f.nameWithoutExtension)) // must still parse
        }
    }

    /** The editor and IDEs read enums from the schema; they must match what the app accepts. */
    @Test fun schemaEnumsMatchTheCode() {
        assertEquals(GameStats.STATS, enumOf("properties/achievements/items/properties/stat"))
        assertEquals(SlotKind.entries.map { it.name }, enumOf("properties/slots/items/properties/kind"))
        val quest = "properties/starterHabits/items/properties"
        assertEquals(AppIcons.habitChoices.keys.toList(), enumOf("$quest/icon"))
        assertEquals(HabitKind.entries.map { it.name }, enumOf("$quest/kind"))
        assertEquals(Difficulty.entries.map { it.name }, enumOf("$quest/difficulty"))
        assertEquals(HabitFrequency.entries.map { it.name }, enumOf("$quest/frequency"))
        val stage = "properties/questLines/items/properties/stages/items/properties"
        assertEquals(GameStats.STATS, enumOf("$stage/objective/properties/stat"))
        assertEquals(ObjectiveMode.entries.map { it.name }, enumOf("$stage/objective/properties/mode"))
        assertEquals(SkinTone.entries.map { it.id }, enumOf("properties/npcs/items/properties/skin"))
    }

    /** Hand-drawn sprites must be registered too; a stale registry means someone forgot to run the generator. */
    @Test fun spriteRegistryMatchesDrawables() {
        val dir = repo("app/src/main/res/drawable-nodpi")
        val files = dir.listFiles()!!.map { it.name }.filter { it.startsWith("px_") && it.endsWith(".png") }.map { it.removeSuffix(".png") }.toSet()
        assertEquals(files, SpriteRegistry.sprites.keys)
    }
}
