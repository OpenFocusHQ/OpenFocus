package org.openfocus.app.blocking

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openfocus.app.data.prefs.ShortsPlatform

/** Least privilege: screen content is only subscribed to inside enabled, in-app short-video feeds. */
class ShortsDetectorTest {
    private val all = ShortsPlatform.entries.toSet()

    @Test fun contentEventsOnlyInsideEnabledVideoApps() {
        assertTrue(ShortsDetector.needsContentEvents(ShortsPlatform.YOUTUBE.packageName, all))
        assertTrue(ShortsDetector.needsContentEvents(ShortsPlatform.INSTAGRAM.packageName, all))
        assertFalse(ShortsDetector.needsContentEvents(ShortsPlatform.INSTAGRAM.packageName, all - ShortsPlatform.INSTAGRAM)) // turned off
        assertFalse(ShortsDetector.needsContentEvents(ShortsPlatform.TIKTOK.packageName, all)) // whole app: which-app is enough
        assertFalse(ShortsDetector.needsContentEvents("com.google.android.apps.nbu.paisa.user", all)) // e.g. a payment app
        assertFalse(ShortsDetector.needsContentEvents("com.whatsapp", all))
        assertFalse(ShortsDetector.needsContentEvents(null, all))
    }

    @Test fun eventMask() {
        assertEquals(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, ShortsDetector.eventMask(false))
        assertEquals(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, ShortsDetector.eventMask(true))
    }
}
