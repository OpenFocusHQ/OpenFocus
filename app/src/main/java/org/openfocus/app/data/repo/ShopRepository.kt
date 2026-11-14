package org.openfocus.app.data.repo

import org.openfocus.app.core.Days
import org.openfocus.app.core.ItemSlot
import org.openfocus.app.core.ShopCatalog
import org.openfocus.app.core.ShopItem
import org.openfocus.app.data.db.AppDatabase
import org.openfocus.app.data.db.InventoryEntity
import org.openfocus.app.data.db.RewardEntity
import org.openfocus.app.data.prefs.SettingsStore
import org.openfocus.app.game.EventTypes
import org.openfocus.app.game.GameEngine

class ShopRepository(db: AppDatabase, private val game: GameEngine, private val settings: SettingsStore) {
    private val dao = db.gameDao()

    val rewards = dao.observeRewards()
    val inventory = dao.observeInventory()
    val achievements = dao.observeAchievements()
    val events = dao.observeEvents()

    suspend fun buy(item: ShopItem): Boolean {
        if (item.price > 0 && !game.spend(item.price, EventTypes.ITEM_BOUGHT, item.id, "Bought ${item.name}")) return false
        dao.addItem(InventoryEntity(item.id))
        equip(item)
        return true
    }

    suspend fun equip(item: ShopItem) = game.updateProfile {
        when (item.slot) {
            ItemSlot.HAT -> it.copy(avatarHat = if (it.avatarHat == item.id) null else item.id)
            ItemSlot.BACKDROP -> it.copy(avatarBackdrop = if (it.avatarBackdrop == item.id) null else item.id)
            ItemSlot.COLOR -> it.copy(avatarColor = item.id)
        }
    }

    suspend fun saveReward(r: RewardEntity) = dao.upsertReward(r.copy(ownerId = game.profile().id, updatedAt = System.currentTimeMillis()))

    suspend fun deleteReward(r: RewardEntity) = dao.upsertReward(r.copy(deleted = true, updatedAt = System.currentTimeMillis()))

    suspend fun redeem(id: String): Boolean {
        val r = dao.reward(id) ?: return false
        if (!game.spend(r.cost, EventTypes.REWARD_REDEEMED, id, "Redeemed ${r.title}")) return false
        dao.upsertReward(r.copy(timesRedeemed = r.timesRedeemed + 1, updatedAt = System.currentTimeMillis()))
        return true
    }

    /** Buy a temporary unlock for a hard-blocked app from the pause screen. */
    suspend fun buyUnlock(pkg: String, minutes: Int): Boolean {
        val cost = settings.value.unlockCost(minutes)
        if (!game.spend(cost, EventTypes.UNLOCK_BOUGHT, pkg, "Unlocked for $minutes min")) return false
        addPass(pkg, minutes, paid = true)
        return true
    }

    /** Pay coins to skip a wait timer; the app then opens for [allowMinutes] as if the wait had finished. */
    suspend fun buySkipWait(pkg: String, waitSeconds: Int, allowMinutes: Int): Boolean {
        val cost = settings.value.skipCost(waitSeconds)
        if (!game.spend(cost, EventTypes.UNLOCK_BOUGHT, pkg, "Skipped the wait")) return false
        addPass(pkg, allowMinutes, paid = true)
        return true
    }

    /** The wait timer ran out: let the user in for [allowMinutes]. Free, and doesn't raise unlock prices. */
    suspend fun grantWaitedPass(pkg: String, allowMinutes: Int) {
        game.apply(EventTypes.WAITED, pkg, emit = false)
        addPass(pkg, allowMinutes, paid = false)
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

    fun ownsDefaults() = ShopCatalog.items.filter { it.price == 0 }
}
