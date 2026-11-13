package org.openfocus.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.openfocus.app.core.Days

/** GitHub-style calendar heatmap of the last [weeks] weeks. Columns = weeks, rows = Mon..Sun. */
@Composable
fun Heatmap(counts: Map<Long, Int>, target: Int, color: Color, today: Long, modifier: Modifier = Modifier, weeks: Int = 18) {
    Canvas(modifier.fillMaxWidth().aspectRatio(weeks / 7f)) {
        val cell = size.width / weeks
        val gap = cell * 0.18f
        val lastWeekStart = Days.weekStart(today)
        for (w in 0 until weeks) {
            val weekStart = lastWeekStart - (weeks - 1 - w) * 7L
            for (d in 0 until 7) {
                val day = weekStart + d
                if (day > today) continue
                val c = counts[day] ?: 0
                val f = if (c == 0) 0f else (c.toFloat() / target.coerceAtLeast(1)).coerceIn(0.35f, 1f)
                drawRoundRect(
                    color = if (c == 0) color.copy(alpha = 0.1f) else color.copy(alpha = f),
                    topLeft = Offset(w * cell + gap / 2, d * cell + gap / 2),
                    size = Size(cell - gap, cell - gap),
                    cornerRadius = CornerRadius(cell * 0.2f),
                )
            }
        }
    }
}
