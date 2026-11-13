package org.openfocus.app

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.launch
import org.openfocus.app.notifications.Notifications
import org.openfocus.app.work.DailyWorker

class OpenFocusApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        DailyWorker.schedule(this)
        container.appScope.launch {
            container.game.profile()
            container.habits.rollover()
            container.game.syncAchievements()
            container.focus.recoverIfNeeded()
        }
    }
}
