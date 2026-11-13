package org.openfocus.app.screenshots

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.ui.components.HeroAvatar
import org.openfocus.app.ui.pixel.HeroPose
import org.openfocus.app.ui.pixel.PixelSprite
import org.openfocus.app.ui.pixel.SkinTone
import org.openfocus.app.ui.pixel.SpriteCatalog
import org.openfocus.app.ui.pixel.SpriteLayer
import org.openfocus.app.ui.pixel.SpriteSheet
import org.openfocus.app.ui.theme.OpenFocusTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every sprite frame to docs/screenshots/sprites.png and checks sheet dimensions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SpriteGalleryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val allSheets: List<SpriteSheet> =
        listOf(SpriteCatalog.coin) + SpriteCatalog.bodies.values + SpriteCatalog.cloaks.values + SpriteCatalog.hats.values +
            SpriteCatalog.backdrops.values

    @Test fun sheetDimensionsMatchCatalog() {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        allSheets.forEach { sheet ->
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val bmp = BitmapFactory.decodeResource(res, sheet.resId, opts)
            val rows = sheet.anims.values.maxOf { it.row } + 1
            val cols = sheet.anims.values.maxOf { it.frames }
            assertEquals(res.getResourceEntryName(sheet.resId) + " width", sheet.frameWidth * cols, bmp.width)
            assertEquals(res.getResourceEntryName(sheet.resId) + " height", sheet.frameHeight * rows, bmp.height)
        }
    }

    @Test fun gallery() {
        compose.setContent {
            OpenFocusTheme {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Idle ×4 · rest · celebrate ×2", style = MaterialTheme.typography.titleMedium)
                    Row {
                        repeat(4) { f -> PixelSprite(SpriteCatalog.heroLayers("color_violet", null)!!, Modifier.size(48.dp), frame = f) }
                        PixelSprite(SpriteCatalog.heroLayers("color_violet", null, HeroPose.REST)!!, Modifier.size(48.dp))
                        repeat(2) { f -> PixelSprite(SpriteCatalog.heroLayers("color_violet", "hat_party", HeroPose.CELEBRATE)!!, Modifier.size(48.dp), frame = f) }
                    }
                    Text("Skin tones × cloaks", style = MaterialTheme.typography.titleMedium)
                    SkinTone.entries.forEach { tone ->
                        Row { SpriteCatalog.cloaks.keys.forEach { PixelSprite(SpriteCatalog.heroLayers(it, null, skin = tone)!!, Modifier.size(48.dp), frame = 0) } }
                    }
                    Text("Hats (frame 0 / frame 1 head bob)", style = MaterialTheme.typography.titleMedium)
                    Row {
                        SpriteCatalog.hats.keys.forEach { h ->
                            PixelSprite(SpriteCatalog.heroLayers("color_teal", h)!!, Modifier.size(48.dp), frame = 0)
                            PixelSprite(SpriteCatalog.heroLayers("color_teal", h)!!, Modifier.size(48.dp), frame = 1)
                        }
                    }
                    Text("Coin", style = MaterialTheme.typography.titleMedium)
                    Row { repeat(6) { f -> PixelSprite(listOf(SpriteLayer(SpriteCatalog.coin, "spin")), Modifier.size(32.dp), frame = f) } }
                    Text("Backdrops (as used in the app)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        HeroAvatar("color_violet", null, null, size = 72.dp, animate = false)
                        HeroAvatar("color_ember", "hat_crown", "bg_sunrise", size = 72.dp, skinTone = "medium", animate = false)
                        HeroAvatar("color_teal", "hat_party", "bg_forest", size = 72.dp, skinTone = "deep", animate = false)
                        HeroAvatar("color_gold", "hat_beanie", "bg_ocean", size = 72.dp, animate = false)
                        HeroAvatar("color_violet", "hat_wizard", "bg_night", size = 72.dp, skinTone = "medium", animate = false)
                    }
                }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "sprites.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
