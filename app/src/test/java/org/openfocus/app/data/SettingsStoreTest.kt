package org.openfocus.app.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.data.prefs.SettingsStore
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    @Test fun pauseScreenSettingsSurviveARestart() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        SettingsStore(ctx).update {
            it.copy(
                defaultGate = BlockGate.HARD, defaultWaitSeconds = 90, defaultAllowMinutes = 45,
                skipCostPer10s = 4, unlockCostPerMinute = 0, escalatingPrices = false,
                pauseQuestion = "Is {app} worth it?", pauseReminder = "Marathon in March!",
            )
        }
        val s = SettingsStore(ctx).value // fresh instance reads from disk
        assertEquals(BlockGate.HARD, s.defaultGate)
        assertEquals(90, s.defaultWaitSeconds)
        assertEquals(45, s.defaultAllowMinutes)
        assertEquals(4, s.skipCostPer10s)
        assertEquals(0, s.unlockCost(15))
        assertEquals(false, s.escalatingPrices)
        assertEquals("Is {app} worth it?", s.pauseQuestion)
        assertEquals("Marathon in March!", s.pauseReminder)
    }

    @Test fun longerPaidUnlocksCostMoreAndSkippingAddsAFee() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SettingsStore(ctx)
        store.update { it.copy(unlockCostPerMinute = 2, skipCostPer10s = 1, escalatingPrices = false) }
        val s = store.value
        assertEquals(10, s.paidUnlockCost(5))
        assertEquals(30, s.paidUnlockCost(15))
        assertEquals(30 + 6, s.paidUnlockCost(15, skipWaitSeconds = 60))
    }

    @Test fun escalationCanBeTurnedOff() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SettingsStore(ctx)
        store.update { it.copy(unlocksToday = 3, unlocksDay = org.openfocus.app.core.Days.today(), escalatingPrices = true) }
        val rising = store.value.skipCost(30)
        store.update { it.copy(escalatingPrices = false) }
        assertEquals(rising / 4, store.value.skipCost(30))
    }
}
