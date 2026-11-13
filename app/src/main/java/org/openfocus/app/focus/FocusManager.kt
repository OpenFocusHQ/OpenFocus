package org.openfocus.app.focus

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openfocus.app.core.GameRules
import org.openfocus.app.data.db.AppDatabase
import org.openfocus.app.data.db.FocusOutcome
import org.openfocus.app.data.db.FocusSessionEntity
import org.openfocus.app.data.db.newId
import org.openfocus.app.data.prefs.ActiveFocus
import org.openfocus.app.data.prefs.SettingsStore
import org.openfocus.app.game.EventTypes
import org.openfocus.app.game.GameEngine
import org.openfocus.app.notifications.Notifications

/**
 * Owns the lifecycle of focus sessions. The running session is persisted in [SettingsStore] so the
 * blocker keeps working and the session can be completed even if the process is killed.
 */
class FocusManager(
    private val context: Context,
    private val db: AppDatabase,
    private val game: GameEngine,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    private val mutex = Mutex()

    val active: StateFlow<ActiveFocus?> = settings.state.map { it.activeFocus }
        .stateIn(scope, SharingStarted.Eagerly, settings.value.activeFocus)

    val recent = db.focusDao().observeRecent()
    fun since(millis: Long) = db.focusDao().observeSince(millis)

    fun start(minutes: Int, label: String, goalId: String?, deep: Boolean) {
        val now = System.currentTimeMillis()
        val session = ActiveFocus(newId(), now, now + minutes * 60_000L, minutes, label, goalId, deep)
        settings.update { it.copy(activeFocus = session) }
        ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
    }

    /** Called by the service when the timer elapses, or at startup for overdue sessions. */
    suspend fun complete() = mutex.withLock {
        val s = settings.value.activeFocus ?: return@withLock
        settings.update { it.copy(activeFocus = null) }
        val minutes = s.plannedMinutes
        db.focusDao().insert(
            FocusSessionEntity(
                id = s.id, ownerId = game.profile().id, startedAt = s.startedAt, endedAt = s.endsAt,
                plannedMinutes = minutes, actualMinutes = minutes, outcome = FocusOutcome.COMPLETED,
                label = s.label, goalId = s.goalId, deepFocus = s.deep,
            ),
        )
        val xp = GameRules.focusXp(minutes, s.deep)
        game.apply(EventTypes.FOCUS_DONE, s.id, xp = xp, coins = GameRules.focusCoins(minutes),
            message = "Focus complete: $minutes minutes of real work. Well earned!")
        Notifications.focusComplete(context, minutes, xp)
    }

    suspend fun abandon() = mutex.withLock {
        val s = settings.value.activeFocus ?: return@withLock
        settings.update { it.copy(activeFocus = null) }
        val now = System.currentTimeMillis()
        val actual = ((now - s.startedAt) / 60_000).toInt()
        db.focusDao().insert(
            FocusSessionEntity(
                id = s.id, ownerId = game.profile().id, startedAt = s.startedAt, endedAt = now,
                plannedMinutes = s.plannedMinutes, actualMinutes = actual, outcome = FocusOutcome.ABANDONED,
                label = s.label, goalId = s.goalId, deepFocus = s.deep,
            ),
        )
        val dmg = if (settings.value.gentleMode) 0 else GameRules.FOCUS_ABANDON_DAMAGE
        // Partial credit for the time actually spent, so stopping is never worse than not starting.
        val xp = actual
        game.apply(EventTypes.FOCUS_ABANDONED, s.id, xp = xp, hp = -dmg,
            message = "Session ended early after $actual min. That still counts — try a shorter one next time?")
        context.stopService(Intent(context, FocusService::class.java))
    }

    suspend fun recoverIfNeeded() {
        val s = settings.value.activeFocus ?: return
        if (s.endsAt <= System.currentTimeMillis()) complete()
        else ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
    }
}
