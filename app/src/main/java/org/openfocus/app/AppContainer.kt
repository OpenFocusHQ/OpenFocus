package org.openfocus.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.openfocus.app.blocking.UsageTracker
import org.openfocus.app.data.db.AppDatabase
import org.openfocus.app.data.prefs.SettingsStore
import org.openfocus.app.data.repo.BlockRepository
import org.openfocus.app.data.repo.GoalRepository
import org.openfocus.app.data.repo.HabitRepository
import org.openfocus.app.data.repo.QuestRepository
import org.openfocus.app.data.repo.ShopRepository
import org.openfocus.app.data.sync.OfflineSyncGateway
import org.openfocus.app.data.sync.SyncGateway
import org.openfocus.app.focus.FocusManager
import org.openfocus.app.game.GameEngine

/** Hand-rolled dependency container; small enough that a DI framework isn't worth it. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db = AppDatabase.build(appContext)
    val settings = SettingsStore(appContext)
    val game = GameEngine(db)
    val habits = HabitRepository(db, game, settings)
    val goals = GoalRepository(db, game)
    val blocks = BlockRepository(db, settings, appScope)
    val shop = ShopRepository(db, game, settings)
    val focus = FocusManager(appContext, db, game, settings, appScope)
    val usage = UsageTracker(appContext)
    val quests = QuestRepository(db, game, settings).also { q -> game.listeners += { e -> q.onEvent(e) } }

    /** Swap for a real implementation to enable online features. */
    val sync: SyncGateway = OfflineSyncGateway
}
