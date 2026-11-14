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
import org.openfocus.app.blocking.UnlockSheet
import org.openfocus.app.blocking.UnlockSheets
import org.openfocus.app.data.db.BlockGate
import org.openfocus.app.data.db.RewardEntity
import org.openfocus.app.data.prefs.Settings
import org.openfocus.app.ui.theme.OpenFocusTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Renders the pause screen in its main states to docs/screenshots/block_*.png. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BlockScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, state: PauseState, sheet: UnlockSheet? = null) {
        compose.mainClock.autoAdvance = false // keep the breathing shield on its first frame
        compose.setContent { OpenFocusTheme { PauseScreenContent(state, {}, {}, {}, {}, sheet = sheet) } }
        compose.mainClock.advanceTimeByFrame()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val waiting = PauseState("Instagram", BlockReason.ALWAYS, gate = BlockGate.WAIT, waitMs = 30_000, elapsedMs = 12_000, allowMinutes = 15)
    private val rewards = listOf(
        RewardEntity(ownerId = "", title = "Evening scroll", cost = 40, unlockPackage = "com.instagram.android", unlockMinutes = 30),
        RewardEntity(ownerId = "", title = "Weekend free time", cost = 150, unlockPackage = RewardEntity.ANY_BLOCKED_APP, unlockMinutes = 60),
    )

    @Test fun waitTimer() = shoot("block_wait", waiting)

    @Test fun waitFinished() = shoot("block_wait_done", waiting.copy(elapsedMs = 30_000))

    @Test fun chooseHowLong() = shoot("block_free_sheet", waiting.copy(elapsedMs = 30_000), UnlockSheets.free(waiting))

    @Test fun payWithCoins() = shoot("block_pay", waiting,
        UnlockSheets.paid(waiting, coins = 120, settings = Settings(), rewards = rewards, pkg = "com.instagram.android"))

    @Test fun customMessage() = shoot("block_custom",
        waiting.copy(question = "Is {app} worth your evening?", reminder = "Half marathon in March. Sleep is training too."))

    @Test fun customLook() = shoot("block_style",
        waiting.copy(icon = org.openfocus.app.blocking.PauseIcon.LOTUS, colors = org.openfocus.app.blocking.PauseColors.INK))

    @Test fun accessibilityDisclosure() {
        compose.mainClock.autoAdvance = false
        compose.setContent { OpenFocusTheme { org.openfocus.app.ui.components.AccessibilityDisclosureDialog({}, {}) } }
        compose.mainClock.advanceTimeByFrame()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)
        org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?.let { d ->
            canvas.drawColor(0x99000000.toInt())
            canvas.save(); canvas.translate((bmp.width - d.width) / 2f, (bmp.height - d.height) / 2f); d.draw(canvas); canvas.restore()
        }
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "accessibility_disclosure.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun hardBlock() = shoot("block_hard", PauseState("Instagram", BlockReason.DAILY_LIMIT, "30 min", BlockGate.HARD))

    @Test fun strictFocus() = shoot("block_strict", PauseState("Instagram", BlockReason.FOCUS, "Essay", BlockGate.HARD, canPay = false))

    @Test fun reelsBlocked() = shoot("block_reels", PauseState("Instagram", BlockReason.SHORTS, "Instagram Reels", canPay = false, backToApp = true))
}
