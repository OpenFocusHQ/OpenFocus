package org.openfocus.app.ui.pixel

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** A sheet + animation drawn as one layer. [followAnchor] offsets it by the driver layer's anchor. */
data class SpriteLayer(
    val sheet: SpriteSheet,
    val anim: String = SpriteCatalog.IDLE,
    val followAnchor: String? = null,
)

/** True when the user turned animations off (Developer options / Accessibility "Remove animations"). */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/**
 * Draws stacked pixel-art layers, crisp (nearest-neighbour) and at a WHOLE-NUMBER scale so every
 * source pixel maps to the same number of screen pixels. The first layer drives the animation.
 */
@Composable
fun PixelSprite(
    layers: List<SpriteLayer>,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    contentDescription: String? = null,
    colorFilter: ColorFilter? = null,
    /** Pin a specific frame of the driver animation (galleries, still images). */
    frame: Int? = null,
) {
    if (layers.isEmpty()) return
    val driver = layers.first()
    val anim = driver.sheet.anim(driver.anim)
    val images: List<ImageBitmap> = layers.map { ImageBitmap.imageResource(it.sheet.resId) }
    val reduceMotion = rememberReduceMotion()

    val frameState: State<Float>? = if (frame == null && animate && !reduceMotion && anim.frames > 1) {
        rememberInfiniteTransition(label = "sprite").animateFloat(
            initialValue = 0f,
            targetValue = anim.frames.toFloat(),
            animationSpec = infiniteRepeatable(tween((anim.frames * 1000 / anim.fps).roundToInt(), easing = LinearEasing)),
            label = "frame",
        )
    } else null

    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(semantics)) {
        @Suppress("NAME_SHADOWING")
        val frame = (frame ?: frameState?.value?.toInt() ?: 0).coerceIn(0, anim.frames - 1)
        val fw = driver.sheet.frameWidth
        val fh = driver.sheet.frameHeight
        val fit = min(size.width / fw, size.height / fh)
        val scale = if (fit >= 1f) floor(fit) else fit // integer scale whenever there's room
        val dstW = (fw * scale).roundToInt()
        val dstH = (fh * scale).roundToInt()
        val left = ((size.width - dstW) / 2f).roundToInt()
        val top = ((size.height - dstH) / 2f).roundToInt()

        layers.forEachIndexed { i, layer ->
            val la = layer.sheet.anim(layer.anim)
            val f = frame.coerceAtMost(la.frames - 1)
            val anchor = layer.followAnchor?.let { driver.sheet.anchor(it, driver.anim, frame) } ?: IntOffset.Zero
            drawImage(
                image = images[i],
                srcOffset = IntOffset(f * layer.sheet.frameWidth, la.row * layer.sheet.frameHeight),
                srcSize = IntSize(layer.sheet.frameWidth, layer.sheet.frameHeight),
                dstOffset = IntOffset(left + (anchor.x * scale).roundToInt(), top + (anchor.y * scale).roundToInt()),
                dstSize = IntSize((layer.sheet.frameWidth * scale).roundToInt(), (layer.sheet.frameHeight * scale).roundToInt()),
                filterQuality = FilterQuality.None,
                colorFilter = colorFilter,
            )
        }
    }
}

/** Convenience for a single sheet. */
@Composable
fun PixelSprite(
    sheet: SpriteSheet,
    anim: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    contentDescription: String? = null,
) = PixelSprite(listOf(SpriteLayer(sheet, anim)), modifier, animate, contentDescription)
