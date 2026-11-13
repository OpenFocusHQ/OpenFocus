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

/**
 * A sheet + animation drawn as one layer.
 * @param offset position of the layer's top-left corner, in source pixels.
 * @param followAnchor name of an anchor (e.g. "head") published by another layer in the stack;
 *        this layer is moved by that anchor's per-frame offset (hats bob with the head).
 */
data class SpriteLayer(
    val sheet: SpriteSheet,
    val anim: String = SpriteCatalog.IDLE,
    val offset: IntOffset = IntOffset.Zero,
    val followAnchor: String? = null,
)

/** True when the user turned animations off (Accessibility "Remove animations" / developer options). */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/**
 * One shared clock period. Every animation's loop length (frames / fps) should divide it evenly
 * so loops stay seamless; 12 s works for 1, 1.5, 2, 3, 4, 6 and 8 fps with typical frame counts.
 */
private const val CLOCK_MS = 12_000

/**
 * Draws stacked pixel-art layers, crisp (nearest-neighbour) and at a WHOLE-NUMBER scale so every
 * source pixel maps to the same number of screen pixels. Each layer animates at its own fps.
 */
@Composable
fun PixelSprite(
    layers: List<SpriteLayer>,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    contentDescription: String? = null,
    colorFilter: ColorFilter? = null,
    /** Pin every layer to this frame (galleries, still images). */
    frame: Int? = null,
    /**
     * Keep pixels perfectly even by using a whole-number scale (default). Set false for large
     * hero images that should fill their width; nearest-neighbour still keeps edges crisp.
     */
    integerScale: Boolean = true,
) {
    if (layers.isEmpty()) return
    val images: List<ImageBitmap> = layers.map { ImageBitmap.imageResource(it.sheet.resId) }
    val reduceMotion = rememberReduceMotion()
    val animated = frame == null && animate && !reduceMotion && layers.any { it.sheet.anim(it.anim).frames > 1 }

    val clock: State<Float>? = if (animated) {
        rememberInfiniteTransition(label = "sprite").animateFloat(
            initialValue = 0f,
            targetValue = CLOCK_MS.toFloat(),
            animationSpec = infiniteRepeatable(tween(CLOCK_MS, easing = LinearEasing)),
            label = "clock",
        )
    } else null

    // Logical canvas = union of all layer rectangles.
    val logicalW = layers.maxOf { it.offset.x + it.sheet.frameWidth }
    val logicalH = layers.maxOf { it.offset.y + it.sheet.frameHeight }

    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(semantics)) {
        val t = clock?.value ?: 0f
        fun frameOf(layer: SpriteLayer): Int {
            val a = layer.sheet.anim(layer.anim)
            val f = frame ?: (t / 1000f * a.fps).toInt()
            return if (frame != null) f.coerceIn(0, a.frames - 1) else f % a.frames
        }

        val fit = min(size.width / logicalW, size.height / logicalH)
        val scale = if (integerScale && fit >= 1f) floor(fit) else fit
        val left = ((size.width - logicalW * scale) / 2f).roundToInt()
        val top = ((size.height - logicalH * scale) / 2f).roundToInt()

        layers.forEachIndexed { i, layer ->
            val a = layer.sheet.anim(layer.anim)
            val f = frameOf(layer)
            val anchor = layer.followAnchor?.let { name ->
                layers.firstOrNull { it !== layer && it.sheet.anchors.containsKey(name) }
                    ?.let { src -> src.sheet.anchor(name, src.anim, frameOf(src)) }
            } ?: IntOffset.Zero
            val x = layer.offset.x + anchor.x
            val y = layer.offset.y + anchor.y
            drawImage(
                image = images[i],
                srcOffset = IntOffset((a.col + f) * layer.sheet.frameWidth, a.row * layer.sheet.frameHeight),
                srcSize = IntSize(layer.sheet.frameWidth, layer.sheet.frameHeight),
                dstOffset = IntOffset(left + (x * scale).roundToInt(), top + (y * scale).roundToInt()),
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
    frame: Int? = null,
) = PixelSprite(listOf(SpriteLayer(sheet, anim)), modifier, animate, contentDescription, frame = frame)
