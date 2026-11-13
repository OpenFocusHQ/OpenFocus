package org.openfocus.app.data.repo

import org.openfocus.app.core.Days
import org.openfocus.app.core.GameRules
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

    /** Buy a temporary unlock for a blocked app from the block screen. */
    suspend fun buyUnlock(pkg: String, minutes: Int): Boolean {
        val today = Days.today()
        val cost = GameRules.unlockCost(minutes, settings.value.unlocksTodayEffective(today))
        if (!game.spend(cost, EventTypes.UNLOCK_BOUGHT, pkg, "Unlocked for $minutes min")) return false
        val until = System.currentTimeMillis() + minutes * 60_000L
        settings.update { s ->
            s.copy(
                unlockPasses = s.unlockPasses.filterValues { it > System.currentTimeMillis() } + (pkg to until),
                unlocksToday = s.unlocksTodayEffective(today) + 1,
                unlocksDay = today,
            )
        }
        return true
    }

    fun ownsDefaults() = ShopCatalog.items.filter { it.price == 0 }
}
