package com.openfocushq.openfocus.data.repo

import kotlinx.coroutines.flow.first
import com.openfocushq.openfocus.core.Days
import com.openfocushq.openfocus.core.ShopCatalog
import com.openfocushq.openfocus.core.content.ItemDef
import com.openfocushq.openfocus.data.db.AppDatabase
import com.openfocushq.openfocus.data.db.InventoryEntity
import com.openfocushq.openfocus.data.db.equippedMap
import com.openfocushq.openfocus.data.db.withEquipped
import com.openfocushq.openfocus.data.db.RewardEntity
import com.openfocushq.openfocus.data.prefs.SettingsStore
import com.openfocushq.openfocus.game.EventTypes
import com.openfocushq.openfocus.game.GameEngine

class ShopRepository(db: AppDatabase, private val game: GameEngine, private val settings: SettingsStore) {
    private val dao = db.gameDao()
    private val blockDao = db.blockDao()

    val rewards = dao.observeRewards()
    val inventory = dao.observeInventory()
    val achievements = dao.observeAchievements()
    val events = dao.observeEvents()

    suspend fun buy(item: ItemDef): Boolean {
        if (item.price > 0 && !game.spend(item.price, EventTypes.ITEM_BOUGHT, item.id, "Bought ${item.name}")) return false
        dao.addItem(InventoryEntity(item.id))
        equip(item)
        return true
    }

    /** Wear [item]; wearing it again takes it off, unless its slot must always be filled. */
    suspend fun equip(item: ItemDef) = game.updateProfile { p ->
        val slot = ShopCatalog.slot(item.slot) ?: return@updateProfile p
        val current = ShopCatalog.resolveEquipped(p.equippedMap())
        val next = if (current[slot.id] == item.id && !slot.required) current - slot.id else current + (slot.id to item.id)
        p.withEquipped(next)
    }

    suspend fun saveReward(r: RewardEntity) = dao.upsertReward(r.copy(ownerId = game.profile().id, updatedAt = System.currentTimeMillis()))

    suspend fun deleteReward(r: RewardEntity) = dao.upsertReward(r.copy(deleted = true, updatedAt = System.currentTimeMillis()))

    /**
     * Redeem a reward at its fixed, self-chosen price (rewards never escalate). If it's linked to
     * an app, also unlock it: [forPackage] when redeemed from that app's pause screen; otherwise the
     * linked app, or every blocked app for an "any blocked app" reward.
     */
    suspend fun redeem(id: String, forPackage: String? = null): Boolean {
        val r = dao.reward(id) ?: return false
        if (forPackage != null && !r.unlocks(forPackage)) return false
        if (!game.spend(r.cost, EventTypes.REWARD_REDEEMED, id, "Redeemed ${r.title}")) return false
        dao.upsertReward(r.copy(timesRedeemed = r.timesRedeemed + 1, updatedAt = System.currentTimeMillis()))
        if (r.unlocksApp) {
            val targets = when {
                forPackage != null -> listOf(forPackage)
                r.unlockPackage == RewardEntity.ANY_BLOCKED_APP -> blockDao.observeRules().first().map { it.packageName }
                else -> listOfNotNull(r.unlockPackage)
            }
            targets.forEach { addPass(it, r.unlockMinutes, paid = false) } // fixed price: doesn't raise unlock prices
        }
        return true
    }

    /**
     * Pay coins to unlock [pkg] for [minutes] (the user picks the length; longer costs more).
     * Pass [skipWaitSeconds] when this also skips a wait timer, which adds the skip fee.
     */
    suspend fun buyUnlock(pkg: String, minutes: Int, skipWaitSeconds: Int? = null): Boolean {
        val cost = settings.value.paidUnlockCost(minutes, skipWaitSeconds)
        val what = if (skipWaitSeconds != null) "Skipped the wait, unlocked for $minutes min" else "Unlocked for $minutes min"
        if (!game.spend(cost, EventTypes.UNLOCK_BOUGHT, pkg, what)) return false
        addPass(pkg, minutes, paid = true)
        return true
    }

    /** The wait timer ran out: let the user in for the [minutes] they chose. Free, and doesn't raise prices. */
    suspend fun grantWaitedPass(pkg: String, minutes: Int) {
        game.apply(EventTypes.WAITED, pkg, emit = false)
        addPass(pkg, minutes, paid = false)
    }

    private fun addPass(pkg: String, minutes: Int, paid: Boolean) {
        val today = Days.today()
        val now = System.currentTimeMillis()
        settings.update { s ->
            s.copy(
                unlockPasses = s.unlockPasses.filterValues { it > now } + (pkg to now + minutes * 60_000L),
                unlocksToday = if (paid) s.unlocksTodayEffective(today) + 1 else s.unlocksTodayEffective(today),
                unlocksDay = today,
            )
        }
    }

}
