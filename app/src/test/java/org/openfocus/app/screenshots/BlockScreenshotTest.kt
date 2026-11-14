package org.openfocus.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.blocking.BlockReason
import org.openfocus.app.blocking.PauseScreenContent
import org.openfocus.app.blocking.PauseState
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.ui.theme.OpenFocusTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Renders the pause screen in its main states to docs/screenshots/block_*.png. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BlockScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, state: PauseState) {
        compose.mainClock.autoAdvance = false // keep the breathing shield on its first frame
        compose.setContent { OpenFocusTheme { PauseScreenContent(state, {}, {}, {}, {}) } }
        compose.mainClock.advanceTimeByFrame()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val nudge = "This app is resting right now. What were you about to do instead?"

    @Test fun waitTimer() = shoot("block_wait",
        PauseState("FeedApp", BlockReason.ALWAYS, gate = BlockGate.WAIT, waitMs = 30_000, elapsedMs = 12_000, allowMinutes = 10, nudge = nudge))

    @Test fun waitFinished() = shoot("block_wait_done",
        PauseState("FeedApp", BlockReason.SCHEDULE, "Evening wind-down", BlockGate.WAIT, 10_000, 10_000, allowMinutes = 15, nudge = nudge))

    @Test fun hardBlock() = shoot("block_hard",
        PauseState("FeedApp", BlockReason.DAILY_LIMIT, "30 min", BlockGate.HARD, nudge = nudge))

    @Test fun strictFocus() = shoot("block_strict",
        PauseState("FeedApp", BlockReason.FOCUS, "Essay", BlockGate.HARD, canPay = false, nudge = nudge))
}
