package com.openfocushq.openfocus

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.launch
import com.openfocushq.openfocus.notifications.Notifications
import com.openfocushq.openfocus.work.DailyWorker

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
