package org.familytools.educationtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.familytools.educationtracker.services.TrendPoint
import kotlin.math.cos
import kotlin.math.sin

private val ChartTeal = Color(0xFF00796B)

/** Hand-rolled Canvas charts — avoids pulling in a Compose charting library
 * (and its own version-compatibility risk) for what's fundamentally a line
 * and a radar plot. */
@Composable
fun LineChartView(points: List<TrendPoint>, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)) {
            if (points.isEmpty()) return@Canvas
            val values = points.map { it.value }
            val minV = (values.minOrNull() ?: 0.0) - 5
            val maxV = (values.maxOrNull() ?: 100.0) + 5
            val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width

            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, p ->
                val x = i * stepX
                val y = size.height - ((p.value - minV) / range * size.height).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(ChartTeal, radius = 5f, center = Offset(x, y))
            }
            drawPath(path, color = ChartTeal, style = Stroke(width = 4f))
        }
    }
}

@Composable
fun RadarChartView(values: Map<String, Double>, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Canvas(modifier = Modifier.fillMaxWidth().height(260.dp).padding(top = 8.dp)) {
            if (values.isEmpty()) return@Canvas
            val n = values.size
            val center = Offset(size.width / 2, size.height / 2)
            val radius = minOf(size.width, size.height) / 2 * 0.75f
            val angleStep = (2 * Math.PI / n)

            // grid rings
            for (ring in 1..4) {
                val ringPath = androidx.compose.ui.graphics.Path()
                for (i in 0..n) {
                    val angle = -Math.PI / 2 + i * angleStep
                    val r = radius * ring / 4f
                    val x = center.x + (r * cos(angle)).toFloat()
                    val y = center.y + (r * sin(angle)).toFloat()
                    if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                }
                drawPath(ringPath, color = Color.LightGray, style = Stroke(width = 1f))
            }

            val entries = values.entries.toList()
            val dataPath = androidx.compose.ui.graphics.Path()
            entries.forEachIndexed { i, (_, value) ->
                val angle = -Math.PI / 2 + i * angleStep
                val r = radius * (value.coerceIn(0.0, 100.0) / 100f)
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y + (r * sin(angle)).toFloat()
                if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                if (i == entries.size - 1) dataPath.close()
                drawCircle(ChartTeal, radius = 5f, center = Offset(x, y))
            }
            drawPath(dataPath, color = ChartTeal.copy(alpha = 0.3f))
            drawPath(dataPath, color = ChartTeal, style = Stroke(width = 3f))
        }
        Text(
            values.keys.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
