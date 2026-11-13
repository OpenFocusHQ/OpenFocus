package org.openfocus.app.screenshots

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.blocking.BlockActivity
import org.openfocus.app.blocking.BlockReason
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Renders the Shield interstitial to docs/screenshots/block.png. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BlockScreenshotTest {
    @Test fun blockScreen() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_PACKAGE, "com.example.feed")
            .putExtra(BlockActivity.EXTRA_REASON, BlockReason.SCHEDULE.name)
            .putExtra(BlockActivity.EXTRA_DETAIL, "Evening wind-down")
        val activity = Robolectric.buildActivity(BlockActivity::class.java, intent).setup().get()
        ShadowLooper.idleMainLooper()
        val view = activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "block.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
