package org.openfocus.app.ui.components

import org.openfocus.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import org.openfocus.app.ui.pixel.AscentTrail
import org.openfocus.app.ui.pixel.PixelSprite
import org.openfocus.app.ui.pixel.SpriteCatalog
import org.openfocus.app.ui.pixel.SpriteLayer

/**
 * The pixel-art "ascent" view for a long-term goal: one mountain per goal colour, a tent for each
 * milestone (lit once reached), a climber at the last camp reached, and a flag on the summit that
 * turns gold when the goal is complete.
 */
@Composable
fun AscentMountain(
    milestones: List<Boolean>,
    completed: Boolean,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    integerScale: Boolean = true,
) {
    // Follow the app theme (not just the system) so the scene never glares on a dark screen.
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val n = milestones.size
    val done = milestones.count { it }
    val target = AscentTrail.climberFraction(done, n, completed)
    val f by animateFloatAsState(target, tween(if (animate) 900 else 0), label = stringResource(R.string.climb))

    val layers = buildList {
        val scene = when {
            night && completed -> SpriteCatalog.SUMMIT_NIGHT
            night -> SpriteCatalog.CLIMBING_NIGHT
            completed -> SpriteCatalog.SUMMIT
            else -> SpriteCatalog.CLIMBING
        }
        add(SpriteLayer(SpriteCatalog.ascent(colorIndex), scene))
        milestones.forEachIndexed { i, reached ->
            val p = AscentTrail.pointAt(AscentTrail.campFraction(i, n))
            add(SpriteLayer(SpriteCatalog.tent, if (reached) SpriteCatalog.LIT else SpriteCatalog.UNLIT, p - IntOffset(4, 6)))
        }
        add(SpriteLayer(SpriteCatalog.flag, if (completed) SpriteCatalog.GOLD else SpriteCatalog.RED, AscentTrail.FLAG_OFFSET))
        // Stand just left of the camp's tent so both stay visible; centred on the trail at the summit.
        val beside = if (completed) IntOffset(5, 11) else IntOffset(5 + AscentTrail.CLIMBER_BESIDE_CAMP, 11)
        add(SpriteLayer(SpriteCatalog.climber, SpriteCatalog.IDLE, AscentTrail.pointAt(f) - beside))
    }
    val description = if (completed) "Summit reached" else "$done of $n camps reached"
    PixelSprite(layers, modifier, animate = animate, contentDescription = description, integerScale = integerScale)
}
