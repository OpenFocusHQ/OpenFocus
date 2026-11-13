package org.openfocus.app.ui.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.openfocus.app.core.ItemSlot
import org.openfocus.app.core.ShopCatalog

class SpriteCatalogTest {
    @Test fun everyHatAndCloakHasASprite() {
        ShopCatalog.items.filter { it.slot == ItemSlot.HAT }.forEach { assertNotNull("hat sprite for ${it.id}", SpriteCatalog.hats[it.id]) }
        ShopCatalog.items.filter { it.slot == ItemSlot.COLOR }.forEach { assertNotNull("cloak sprite for ${it.id}", SpriteCatalog.cloaks[it.id]) }
    }

    @Test fun heroLayersStackBodyCloakHat() {
        val layers = SpriteCatalog.heroLayers("color_teal", "hat_crown")!!
        assertEquals(listOf(SpriteCatalog.heroBody, SpriteCatalog.cloaks["color_teal"], SpriteCatalog.hats["hat_crown"]), layers.map { it.sheet })
        assertEquals("head", layers.last().followAnchor)
        assertEquals(2, SpriteCatalog.heroLayers(null, null)!!.size)
    }

    @Test fun anchorsCoverEveryFrame() {
        val body = SpriteCatalog.heroBody
        body.anchors.values.forEach { perAnim ->
            perAnim.forEach { (anim, offsets) -> assertEquals("anchor frames for $anim", body.anim(anim).frames, offsets.size) }
        }
    }

    @Test fun layersOfOneCharacterShareFrameSize() {
        val sheets = listOf(SpriteCatalog.heroBody) + SpriteCatalog.cloaks.values + SpriteCatalog.hats.values
        sheets.forEach { assertEquals(32, it.frameWidth); assertEquals(32, it.frameHeight) }
    }
}
