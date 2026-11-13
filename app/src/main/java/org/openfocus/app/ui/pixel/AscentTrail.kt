package org.openfocus.app.ui.pixel

import androidx.compose.ui.unit.IntOffset
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The winding trail on the 160x112 ascent scene, in scene pixels, from the base to just below
 * the summit. MUST match TRAIL in art/tools/build_placeholders.py (the dashes are baked in there).
 */
object AscentTrail {
    val POINTS: List<IntOffset> = listOf(
        IntOffset(30, 104), IntOffset(118, 92), IntOffset(52, 78), IntOffset(112, 62),
        IntOffset(66, 46), IntOffset(98, 32), IntOffset(88, 18),
    )

    /** Horizontal shift (scene px) so the climber stands beside a tent rather than on it. */
    const val CLIMBER_BESIDE_CAMP = 7

    /** The flag's pole stands on the peak at (88, 10); its sprite is 8x8 with the pole at x=0. */
    val FLAG_OFFSET = IntOffset(88, 2)

    private val segLengths = POINTS.zipWithNext { a, b -> hypot((b.x - a.x).toFloat(), (b.y - a.y).toFloat()) }
    private val total = segLengths.sum()

    /** Point at fraction [f] (0..1) of the trail's length, snapped to whole pixels. */
    fun pointAt(f: Float): IntOffset {
        var remaining = f.coerceIn(0f, 1f) * total
        for ((i, len) in segLengths.withIndex()) {
            if (remaining <= len) {
                val a = POINTS[i]
                val b = POINTS[i + 1]
                val t = if (len == 0f) 0f else remaining / len
                return IntOffset((a.x + (b.x - a.x) * t).roundToInt(), (a.y + (b.y - a.y) * t).roundToInt())
            }
            remaining -= len
        }
        return POINTS.last()
    }

    /** Camps sit at even intervals with the summit as the final stop: camp i at (i+1)/(n+1). */
    fun campFraction(index: Int, count: Int): Float = (index + 1f) / (count + 1f)

    /** Where the climber stands: at the last camp reached, or at the top when the goal is complete. */
    fun climberFraction(campsDone: Int, count: Int, completed: Boolean): Float =
        if (completed) 1f else campsDone.toFloat() / (count + 1f)
}
