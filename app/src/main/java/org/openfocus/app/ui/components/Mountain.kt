package org.openfocus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp

/**
 * PLACEHOLDER ART: the "ascent" view for a long-term goal. The trail zig-zags up the mountain;
 * camps are milestones and the climber sits at the current progress.
 */
@Composable
fun AscentMountain(
    progress: Float,
    milestones: List<Boolean>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ascent")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Sky
        drawRect(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.85f), lerp(color, Color.White, 0.6f))))
        // Back range
        val back = Path().apply {
            moveTo(0f, h * 0.75f); lineTo(w * 0.18f, h * 0.45f); lineTo(w * 0.32f, h * 0.62f)
            lineTo(w * 0.82f, h * 0.35f); lineTo(w, h * 0.55f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(back, lerp(color, Color.White, 0.45f))
        // Main peak
        val peak = Offset(w * 0.55f, h * 0.08f)
        val main = Path().apply {
            moveTo(w * 0.02f, h); lineTo(peak.x, peak.y); lineTo(w * 0.98f, h); close()
        }
        drawPath(main, lerp(color, Color.Black, 0.15f))
        val snow = Path().apply {
            moveTo(peak.x, peak.y); lineTo(peak.x - w * 0.1f, h * 0.26f); lineTo(peak.x - w * 0.03f, h * 0.23f)
            lineTo(peak.x + w * 0.02f, h * 0.28f); lineTo(peak.x + w * 0.09f, h * 0.24f); close()
        }
        drawPath(snow, Color.White)

        // Trail: zig-zag points from base to summit
        val pts = listOf(
            Offset(w * 0.2f, h * 0.95f), Offset(w * 0.7f, h * 0.8f), Offset(w * 0.35f, h * 0.62f),
            Offset(w * 0.65f, h * 0.45f), Offset(w * 0.45f, h * 0.3f), peak,
        )
        val trail = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(trail, Color.White.copy(alpha = 0.8f), style = Stroke(width = w * 0.008f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))

        fun pointAt(f: Float): Offset {
            val segs = pts.zipWithNext()
            val lens = segs.map { (a, b) -> (b - a).getDistance() }
            var remaining = lens.sum() * f
            for ((i, s) in segs.withIndex()) {
                if (remaining <= lens[i]) return s.first + (s.second - s.first) * (remaining / lens[i])
                remaining -= lens[i]
            }
            return peak
        }
        // Camps (milestones)
        milestones.forEachIndexed { i, done ->
            val p = pointAt((i + 1f) / (milestones.size + 0f).coerceAtLeast(1f))
            drawCircle(if (done) Color(0xFFFFC857) else Color.White, radius = w * 0.022f, center = p)
            drawCircle(Color(0xFF2B2440), radius = w * 0.022f, center = p, style = Stroke(w * 0.006f))
        }
        // Climber
        val c = pointAt(animated)
        drawCircle(Color(0xFFF0506E), radius = w * 0.032f, center = c)
        drawCircle(Color.White, radius = w * 0.012f, center = c)
        // Summit flag
        drawLine(Color(0xFF2B2440), peak, peak + Offset(0f, -h * 0.07f), strokeWidth = w * 0.006f)
        val flag = Path().apply {
            moveTo(peak.x, peak.y - h * 0.07f); lineTo(peak.x + w * 0.06f, peak.y - h * 0.055f); lineTo(peak.x, peak.y - h * 0.04f); close()
        }
        drawPath(flag, if (animated >= 0.999f) Color(0xFFFFC857) else Color(0xFFF0506E))
    }
}
