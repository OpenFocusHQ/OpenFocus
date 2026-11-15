package com.openfocushq.openfocus.focus

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.openfocushq.openfocus.OpenFocusApp
import com.openfocushq.openfocus.notifications.Notifications

/** Foreground service that keeps the focus countdown alive and completes the session on time. */
class FocusService : LifecycleService() {
    private var timer: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val container = (application as OpenFocusApp).container
        val session = container.settings.value.activeFocus
        val notification = Notifications.focusOngoing(this, session)
        ServiceCompat.startForeground(
            this, Notifications.FOCUS_ONGOING_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        timer?.cancel()
        timer = lifecycleScope.launch {
            val remaining = session.endsAt - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            if (container.settings.value.activeFocus?.id == session.id) container.focus.complete()
            stopSelf()
        }
        return START_STICKY
    }
}
