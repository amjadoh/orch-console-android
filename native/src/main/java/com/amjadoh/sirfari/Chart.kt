package com.amjadoh.sirfari

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** رسم آخر ساعة: المعالج والذاكرة (٪). الأقدم يمين والأحدث يسار، مثل اتجاه القراءة. */
@Composable
fun HistoryChart(points: List<Point>) {
    if (points.size < 3) return
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Panel("آخر ساعة") {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            for (y in listOf(0f, 0.5f, 1f)) drawLine(grid, Offset(0f, size.height * y), Offset(size.width, size.height * y), 1f)
            val t0 = points.first().at.toFloat(); val span = (points.last().at - points.first().at).coerceAtLeast(1).toFloat()
            fun line(sel: (Point) -> Double?, color: Color) {
                val path = Path(); var started = false
                for (p in points) {
                    val v = sel(p) ?: continue
                    val x = size.width - (p.at - t0) / span * size.width
                    val y = size.height * (1f - (v / 100.0).toFloat().coerceIn(0f, 1f))
                    if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 4f))
            }
            line({ it.mem }, Tone.run)
            line({ it.cpu }, Tone.ok)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Dot(Tone.ok); Text("المعالج", style = MaterialTheme.typography.labelMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Dot(Tone.run); Text("الذاكرة", style = MaterialTheme.typography.labelMedium) }
        }
    }
}
