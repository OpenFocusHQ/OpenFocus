package org.openfocus.app.core

enum class ItemSlot { HAT, BACKDROP, COLOR }

/**
 * Built-in cosmetic items. Art is placeholder vector/canvas drawing (see ui/components/Avatar.kt);
 * contributors can replace it with real CC-BY / CC0 art keyed by [id].
 */
data class ShopItem(
    val id: String,
    val name: String,
    val slot: ItemSlot,
    val price: Int,
    val minLevel: Int = 1,
    /** ARGB color used by the placeholder renderer. */
    val color: Long,
)

object ShopCatalog {
    val items = listOf(
        ShopItem("color_violet", "Violet Cloak", ItemSlot.COLOR, 0, color = 0xFF7C6CF2),
        ShopItem("color_teal", "Teal Cloak", ItemSlot.COLOR, 40, color = 0xFF2BB3A3),
        ShopItem("color_ember", "Ember Cloak", ItemSlot.COLOR, 60, color = 0xFFF06A4D),
        ShopItem("color_gold", "Golden Cloak", ItemSlot.COLOR, 250, minLevel = 10, color = 0xFFE0A526),
        ShopItem("hat_beanie", "Cosy Beanie", ItemSlot.HAT, 50, color = 0xFFE85D75),
        ShopItem("hat_party", "Party Hat", ItemSlot.HAT, 80, minLevel = 3, color = 0xFF4DA3F0),
        ShopItem("hat_wizard", "Wizard Hat", ItemSlot.HAT, 180, minLevel = 6, color = 0xFF5B4BC4),
        ShopItem("hat_crown", "Summit Crown", ItemSlot.HAT, 400, minLevel = 12, color = 0xFFFFC857),
        ShopItem("bg_sunrise", "Sunrise", ItemSlot.BACKDROP, 60, color = 0xFFFFB38A),
        ShopItem("bg_forest", "Forest", ItemSlot.BACKDROP, 90, minLevel = 2, color = 0xFF3E8E5E),
        ShopItem("bg_ocean", "Ocean", ItemSlot.BACKDROP, 120, minLevel = 4, color = 0xFF2F6FB5),
        ShopItem("bg_night", "Starry Night", ItemSlot.BACKDROP, 200, minLevel = 8, color = 0xFF1E1B4B),
    )

    fun byId(id: String?) = items.firstOrNull { it.id == id }
    val defaultColor = items.first()
}
