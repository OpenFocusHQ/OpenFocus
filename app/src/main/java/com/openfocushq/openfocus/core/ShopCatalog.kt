package com.openfocushq.openfocus.core

import com.openfocushq.openfocus.core.content.GameContent
import com.openfocushq.openfocus.core.content.ItemDef
import com.openfocushq.openfocus.core.content.SlotDef

/** Cosmetic slots and items, straight from content.json. */
object ShopCatalog {
    val slots: List<SlotDef> get() = GameContent.current.slots.sortedBy { it.layer }
    val items: List<ItemDef> get() = GameContent.current.items

    fun byId(id: String?): ItemDef? = GameContent.current.item(id)
    fun slot(id: String): SlotDef? = GameContent.current.slot(id)
    fun itemsIn(slot: String): List<ItemDef> = GameContent.current.itemsIn(slot)

    /** Items owned from the start (price 0). */
    val freeItems: List<ItemDef> get() = items.filter { it.price == 0 }

    /** What a profile is wearing: explicit choices plus defaults for required slots. */
    fun resolveEquipped(equipped: Map<String, String>): Map<String, String> {
        val out = equipped.filterKeys { slot(it) != null }.filterValues { byId(it) != null }.toMutableMap()
        slots.filter { it.required && it.id !in out }.forEach { s -> s.defaultItem?.let { out[s.id] = it } }
        return out
    }
}
