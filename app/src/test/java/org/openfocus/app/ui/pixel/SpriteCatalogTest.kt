package org.openfocus.app.ui.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.openfocus.app.core.ShopCatalog
import org.openfocus.app.core.content.SlotKind

class SpriteCatalogTest {
    private val cloakSlot = ShopCatalog.slots.first { it.kind == SlotKind.BODY }.id
    private fun items(kind: SlotKind) = ShopCatalog.items.filter { ShopCatalog.slot(it.slot)?.kind == kind }

    @Test fun everyItemWithASpriteResolves() {
        ShopCatalog.items.filter { it.sprite != null }.forEach { assertNotNull("sprite for ${it.id}", SpriteCatalog.itemSheet(it)) }
        assertNotNull(SpriteCatalog.defaultBackdrop)
    }

    @Test fun heroLayersFollowSlotOrder() {
        val layers = SpriteCatalog.heroLayers(mapOf("cloak" to "color_teal", "armour" to "armour_iron", "hat" to "hat_crown"))!!
        val expected = listOf(
            SpriteCatalog.heroBody,
            SpriteCatalog.itemSheet(ShopCatalog.byId("color_teal")!!),
            SpriteCatalog.itemSheet(ShopCatalog.byId("armour_iron")!!),
            SpriteCatalog.itemSheet(ShopCatalog.byId("hat_crown")!!),
        )
        assertEquals(expected, layers.map { it.sheet })
        assertEquals("body", layers[2].followAnchor)
        assertEquals("head", layers[3].followAnchor)
    }

    @Test fun requiredSlotsAreFilledByDefault() {
        // Nothing equipped: body + the required cloak's default item.
        val layers = SpriteCatalog.heroLayers(emptyMap())!!
        assertEquals(2, layers.size)
        val default = ShopCatalog.slot(cloakSlot)!!.defaultItem!!
        assertEquals(SpriteCatalog.itemSheet(ShopCatalog.byId(default)!!), layers[1].sheet)
    }

    @Test fun unknownOrMissingArtFallsBack() {
        assertEquals(2, SpriteCatalog.heroLayers(mapOf("hat" to "no_such_item"))!!.size) // ignored
    }

    @Test fun everyBackdropAndPoseResolves() {
        items(SlotKind.BACKDROP).forEach { bg ->
            HeroPose.entries.forEach { pose ->
                SkinTone.entries.forEach { tone ->
                    val layers = SpriteCatalog.heroLayers(
                        mapOf("cloak" to "color_gold", "hat" to "hat_wizard", "backdrop" to bg.id), pose, tone, withBackdrop = true,
                    )!!
                    assertEquals(4, layers.size)
                    assertEquals(SpriteCatalog.HERO_IN_BACKDROP, layers[1].offset)
                    assertEquals(pose.anim, layers[1].anim)
                }
            }
        }
    }

    @Test fun bodyAnchorsCoverEveryFrame() {
        SpriteCatalog.bodies.values.forEach { body ->
            listOf("head", "body").forEach { anchor ->
                assertEquals(body.anims.keys, body.anchors.getValue(anchor).keys)
                body.anchors.getValue(anchor).forEach { (anim, offsets) -> assertEquals("$anchor/$anim", body.anim(anim).frames, offsets.size) }
            }
        }
    }

    @Test fun bodySlotSheetsMatchEveryBodyAnimation() {
        items(SlotKind.BODY).mapNotNull(SpriteCatalog::itemSheet).forEach { assertEquals(SpriteCatalog.heroBody.anims, it.anims) }
    }

    @Test fun everyAscentHasDayAndNightScenes() {
        val names = setOf(SpriteCatalog.CLIMBING, SpriteCatalog.SUMMIT, SpriteCatalog.CLIMBING_NIGHT, SpriteCatalog.SUMMIT_NIGHT)
        SpriteCatalog.ascents.forEach { assertEquals(names, it.anims.keys) }
        assertEquals(SpriteCatalog.ascent(1), SpriteCatalog.ascent(9)) // colour index wraps
    }

    /** A loop that doesn't divide the shared clock would visibly jump once every 12 s. */
    @Test fun everyAnimationLoopsSeamlessly() {
        val sheets = SpriteCatalog.bodies.values + ShopCatalog.items.mapNotNull(SpriteCatalog::itemSheet) +
            listOfNotNull(SpriteCatalog.defaultBackdrop) + SpriteCatalog.ascents +
            listOf(SpriteCatalog.tent, SpriteCatalog.climber, SpriteCatalog.flag, SpriteCatalog.coin,
                SpriteCatalog.sparkle, SpriteCatalog.shield, SpriteCatalog.badges, SpriteCatalog.empty)
        sheets.flatMap { it.anims.entries }.filter { it.value.frames > 1 }.forEach { (name, a) ->
            val loopMs = a.frames * 1000.0 / a.fps
            val loops = CLOCK_MS / loopMs
            assertEquals("'$name' loop of ${loopMs}ms doesn't divide $CLOCK_MS ms", Math.round(loops).toDouble(), loops, 1e-6)
        }
    }

    @Test fun skinToneDefaultsToLight() {
        assertEquals(SkinTone.LIGHT, SkinTone.of(null))
        assertEquals(SkinTone.DEEP, SkinTone.of("deep"))
    }

    @Test fun missingSpriteMeansNoSheet() {
        assertNull(SpriteCatalog.backdropSheet("px_does_not_exist"))
    }
}
