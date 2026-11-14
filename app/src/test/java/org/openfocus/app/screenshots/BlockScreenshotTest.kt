package org.openfocus.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.blocking.BlockReason
import org.openfocus.app.blocking.PaidUnlockDialog
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

    private fun shoot(name: String, state: PauseState, dialog: (@androidx.compose.runtime.Composable () -> Unit)? = null) {
        compose.mainClock.autoAdvance = false // keep the breathing shield on its first frame
        compose.setContent { OpenFocusTheme { PauseScreenContent(state, {}, {}, {}, {}); dialog?.invoke() } }
        compose.mainClock.advanceTimeByFrame()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)
        org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { d ->
            canvas.drawColor(0x99000000.toInt())
            canvas.save(); canvas.translate((bmp.width - d.width) / 2f, (bmp.height - d.height) / 2f); d.draw(canvas); canvas.restore()
        }
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val nudge = "This app is resting right now. What were you about to do instead?"

    @Test fun waitTimer() = shoot("block_wait",
        PauseState("FeedApp", BlockReason.ALWAYS, gate = BlockGate.WAIT, waitMs = 30_000, elapsedMs = 12_000, allowMinutes = 10, nudge = nudge))

    @Test fun waitFinished() = shoot("block_wait_done",
        PauseState("FeedApp", BlockReason.SCHEDULE, "Evening wind-down", BlockGate.WAIT, 10_000, 10_000, allowMinutes = 15,
            selectedMinutes = 10, nudge = nudge))

    @Test fun customMessage() = shoot("block_custom",
        PauseState("Instagram", BlockReason.ALWAYS, gate = BlockGate.WAIT, waitMs = 60_000, elapsedMs = 15_000, allowMinutes = 5,
            question = "Is {app} worth your evening?", reminder = "Half marathon in March. Sleep is training too."))

    @Test fun paidUnlockChooser() = shoot("block_pay",
        PauseState("FeedApp", BlockReason.ALWAYS, gate = BlockGate.WAIT, waitMs = 60_000, elapsedMs = 20_000, allowMinutes = 10, nudge = nudge),
    ) {
        PaidUnlockDialog("FeedApp", coins = 120, minutes = 15, onMinutes = {}, perMinute = 2, timeCost = 30,
            skipWaitSeconds = 60, skipCost = 6, escalating = true, onUnlock = {}, onDismiss = {})
    }

    @Test fun hardBlock() = shoot("block_hard",
        PauseState("FeedApp", BlockReason.DAILY_LIMIT, "30 min", BlockGate.HARD, nudge = nudge))

    @Test fun strictFocus() = shoot("block_strict",
        PauseState("FeedApp", BlockReason.FOCUS, "Essay", BlockGate.HARD, canPay = false, nudge = nudge))
}
