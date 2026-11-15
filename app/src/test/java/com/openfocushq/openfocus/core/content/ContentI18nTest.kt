package com.openfocushq.openfocus.core.content

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ContentI18nTest {
    private val src = """
        {"items":[{"id":"hat","slot":"head","name":"Hat","color":"#FFFFFF"}],
         "questLines":[{"id":"q","title":"Climb","npc":"mira","stages":[
            {"id":"s1","title":"Pack","story":"Get ready, {hero}.","intro":[{"speaker":"mira","text":"Hi!"}],
             "collect":{"item":"marker","itemPlural":"markers","icon":"Flag","amount":3},"reward":{"item":"hat"}}]}],
         "npcs":[{"id":"mira","name":"Mira","dialogue":{"greeting":["Hello","Hey"]}}],
         "messages":{"general":["Go!"]}}
    """.trimIndent()

    @Test fun keysCoverTextButNotIds() {
        val keys = ContentI18n.keys(Json.parseToJsonElement(src))
        assertEquals("Hat", keys["items.hat.name"])
        assertEquals("Get ready, {hero}.", keys["questLines.q.stages.s1.story"])
        assertEquals("Hi!", keys["questLines.q.stages.s1.intro.0.text"])
        assertEquals("marker", keys["questLines.q.stages.s1.collect.item"])
        assertEquals("Hey", keys["npcs.mira.dialogue.greeting.1"])
        assertEquals("Go!", keys["messages.general.0"])
        listOf("items.hat.id", "items.hat.slot", "items.hat.color", "questLines.q.npc", "questLines.q.stages.s1.reward.item",
            "questLines.q.stages.s1.intro.0.speaker", "questLines.q.stages.s1.collect.icon").forEach {
            assertFalse("$it must not be translatable", it in keys)
        }
    }

    @Test fun overlayReplacesOnlyGivenText() {
        val out = ContentI18n.apply(Json.parseToJsonElement(src), mapOf("items.hat.name" to "Sombrero", "npcs.mira.dialogue.greeting.0" to "Hola", "items.hat.slot" to "nope", "messages.general.0" to " "))
        val keys = ContentI18n.keys(out)
        assertEquals("Sombrero", keys["items.hat.name"])
        assertEquals("Hola", keys["npcs.mira.dialogue.greeting.0"])
        assertEquals("Hey", keys["npcs.mira.dialogue.greeting.1"]) // untranslated falls back to English
        assertEquals("Go!", keys["messages.general.0"]) // blank means "not translated yet"
        assertTrue(out.toString().contains("\"slot\":\"head\"")) // ids are never touched
    }

    @Test fun overlayFileSkipsMetadata() {
        assertEquals(mapOf("a.b" to "c"), ContentI18n.parseOverlay("""{"${'$'}language":"es","a.b":"c"}"""))
    }

    @Test fun localeCandidatesMostSpecificFirst() {
        assertEquals(listOf("pt-BR", "pt"), GameContent.candidates(Locale.forLanguageTag("pt-BR")))
        assertEquals(listOf("es"), GameContent.candidates(Locale.forLanguageTag("es")))
        assertEquals(emptyList<String>(), GameContent.candidates(Locale.US))
    }
}
