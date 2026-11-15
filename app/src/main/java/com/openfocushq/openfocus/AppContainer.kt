package com.openfocushq.openfocus

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.openfocushq.openfocus.blocking.UsageTracker
import com.openfocushq.openfocus.data.db.AppDatabase
import com.openfocushq.openfocus.data.prefs.SettingsStore
import com.openfocushq.openfocus.data.repo.BlockRepository
import com.openfocushq.openfocus.data.repo.GoalRepository
import com.openfocushq.openfocus.data.repo.HabitRepository
import com.openfocushq.openfocus.core.Strings
import com.openfocushq.openfocus.data.repo.QuestRepository
import com.openfocushq.openfocus.data.repo.ShopRepository
import com.openfocushq.openfocus.data.sync.OfflineSyncGateway
import com.openfocushq.openfocus.data.sync.SyncGateway
import com.openfocushq.openfocus.focus.FocusManager
import com.openfocushq.openfocus.game.GameEngine

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
    val quests = QuestRepository(db, game, settings, Strings.of(appContext)).also { q -> game.listeners += { e -> q.onEvent(e) } }

    /** Swap for a real implementation to enable online features. */
    val sync: SyncGateway = OfflineSyncGateway
}
