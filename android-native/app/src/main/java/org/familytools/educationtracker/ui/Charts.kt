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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.familytools.educationtracker.services.TrendPoint
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val ChartTeal = Color(0xFF00796B)

/** One labeled bar for [BarChartView] — [value] drives the bar's height
 * (always on a 0-100 scale so percentage and "marks" modes share one
 * renderer), [displayValue] is the text drawn on top of it (which can be a
 * different, more meaningful string, e.g. "348/900" while value=38.7). */
data class BarItem(val label: String, val value: Double, val displayValue: String)

/** Hand-rolled Canvas charts — avoids pulling in a Compose charting library
 * (and its own version-compatibility risk) for what's fundamentally a line,
 * a radar plot, and a bar chart. All three draw real axes/gridlines and
 * label every data point directly (value + category), rather than relying
 * on a caption underneath the chart to explain what each mark means. */
@Composable
fun LineChartView(points: List<TrendPoint>, title: String, modifier: Modifier = Modifier, valueSuffix: String = "%") {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (points.isEmpty()) {
            Text(
                "Not enough data yet.", style = MaterialTheme.typography.bodySmall,
                color = labelColor, modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(280.dp).padding(top = 8.dp)) {
            val leftPad = 40f
            val bottomPad = 70f
            val topPad = 16f
            val rightPad = 12f
            val plotWidth = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            val values = points.map { it.value }
            val rawMin = values.minOrNull() ?: 0.0
            val rawMax = values.maxOrNull() ?: 100.0
            val minV = (floor(rawMin / 10) * 10 - 5).coerceAtLeast(0.0)
            val maxV = (ceil(rawMax / 10) * 10 + 5)
            val range = (maxV - minV).takeIf { it > 0 } ?: 1.0

            // Y axis gridlines + value labels.
            val ySteps = 4
            for (i in 0..ySteps) {
                val v = minV + range * i / ySteps
                val y = topPad + plotHeight - (plotHeight * i / ySteps).toFloat()
                drawLine(axisColor, Offset(leftPad, y), Offset(leftPad + plotWidth, y), strokeWidth = 1f)
                drawText(
                    textMeasurer, "%.0f".format(v),
                    topLeft = Offset(0f, y - 6f),
                    style = TextStyle(fontSize = 9.sp, color = labelColor),
                )
            }
            drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, topPad + plotHeight), strokeWidth = 1.5f)
            drawLine(
                axisColor, Offset(leftPad, topPad + plotHeight), Offset(leftPad + plotWidth, topPad + plotHeight),
                strokeWidth = 1.5f,
            )

            val stepX = if (points.size > 1) plotWidth / (points.size - 1) else 0f
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, p ->
                val x = leftPad + if (points.size > 1) i * stepX else plotWidth / 2
                val y = topPad + plotHeight - ((p.value - minV) / range * plotHeight).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(ChartTeal, radius = 5f, center = Offset(x, y))

                // Value label above each point — the actual mark scored.
                // valueSuffix ("%" by default) is appended AFTER formatting,
                // never spliced into the format template itself — a bare "%"
                // left dangling in a format string crashes with
                // UnknownFormatConversionException (see AnalyticsScreen's
                // confidence-percentage crash, same root cause).
                val valueLabel = "%.0f".format(p.value) + valueSuffix
                val measured = textMeasurer.measure(valueLabel, style = TextStyle(fontSize = 9.sp))
                drawText(
                    textMeasurer, valueLabel,
                    topLeft = Offset(x - measured.size.width / 2, (y - measured.size.height - 6f).coerceAtLeast(0f)),
                    style = TextStyle(fontSize = 9.sp, color = ChartTeal),
                )

                // X axis label (year/term/exam) — rotated so labels don't overlap.
                rotate(degrees = 40f, pivot = Offset(x, topPad + plotHeight + 6f)) {
                    drawText(
                        textMeasurer, p.label,
                        topLeft = Offset(x, topPad + plotHeight + 6f),
                        style = TextStyle(fontSize = 9.sp, color = labelColor),
                    )
                }
            }
            drawPath(path, color = ChartTeal, style = Stroke(width = 4f))
        }
    }
}

@Composable
fun RadarChartView(values: Map<String, Double>, title: String, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (values.isEmpty()) {
            Text(
                "Not enough data yet.", style = MaterialTheme.typography.bodySmall,
                color = labelColor, modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)) {
            val n = values.size
            val center = Offset(size.width / 2, size.height / 2)
            // Radius is deliberately smaller than before (0.55 vs 0.75) to
            // leave room around the edge for each vertex's subject label —
            // previously the subject names only appeared in a single caption
            // line below the whole chart, not next to their actual point.
            val radius = minOf(size.width, size.height) / 2 * 0.55f
            val angleStep = (2 * Math.PI / n)

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
            entries.forEachIndexed { i, (subject, value) ->
                val angle = -Math.PI / 2 + i * angleStep
                val r = radius * (value.coerceIn(0.0, 100.0) / 100f)
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y + (r * sin(angle)).toFloat()
                if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                if (i == entries.size - 1) dataPath.close()
                drawCircle(ChartTeal, radius = 5f, center = Offset(x, y))

                // Subject name + score, placed just outside the outer ring
                // in the same direction as this vertex — directly marks
                // which subject each point on the graph corresponds to.
                val labelR = radius + 16f
                val lx = center.x + (labelR * cos(angle)).toFloat()
                val ly = center.y + (labelR * sin(angle)).toFloat()
                // subject is free text (see LineChartView's valueLabel comment
                // above) — format the number alone, then concatenate.
                val text = "$subject " + "%.0f%%".format(value)
                val measured = textMeasurer.measure(text, style = TextStyle(fontSize = 10.sp))
                val clampedX = (lx - measured.size.width / 2).coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                val clampedY = (ly - measured.size.height / 2).coerceIn(0f, (size.height - measured.size.height).coerceAtLeast(0f))
                drawText(
                    textMeasurer, text,
                    topLeft = Offset(clampedX, clampedY),
                    style = TextStyle(fontSize = 10.sp, color = labelColor),
                )
            }
            drawPath(dataPath, color = ChartTeal.copy(alpha = 0.3f))
            drawPath(dataPath, color = ChartTeal, style = Stroke(width = 3f))
        }
    }
}

/** Bar chart for Reports — overall/subject marks by exam, either as a
 * percentage or as actual marks (via [BarItem.displayValue]); [item.value]
 * is always 0-100 so both modes share the same vertical scale. */
@Composable
fun BarChartView(bars: List<BarItem>, title: String, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (bars.isEmpty()) {
            Text(
                "Not enough data yet.", style = MaterialTheme.typography.bodySmall,
                color = labelColor, modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp)) {
            val leftPad = 40f
            val bottomPad = 80f
            val topPad = 20f
            val rightPad = 12f
            val plotWidth = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
            val maxV = 100.0

            val ySteps = 4
            for (i in 0..ySteps) {
                val v = maxV * i / ySteps
                val y = topPad + plotHeight - (plotHeight * i / ySteps).toFloat()
                drawLine(axisColor, Offset(leftPad, y), Offset(leftPad + plotWidth, y), strokeWidth = 1f)
                drawText(
                    textMeasurer, "%.0f".format(v),
                    topLeft = Offset(0f, y - 6f),
                    style = TextStyle(fontSize = 9.sp, color = labelColor),
                )
            }
            drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, topPad + plotHeight), strokeWidth = 1.5f)
            drawLine(
                axisColor, Offset(leftPad, topPad + plotHeight), Offset(leftPad + plotWidth, topPad + plotHeight),
                strokeWidth = 1.5f,
            )

            val slotWidth = plotWidth / bars.size
            val barWidth = (slotWidth * 0.55f).coerceAtLeast(4f)
            bars.forEachIndexed { i, bar ->
                val slotCenter = leftPad + slotWidth * i + slotWidth / 2
                val barHeight = (bar.value.coerceIn(0.0, maxV) / maxV * plotHeight).toFloat()
                val top = topPad + plotHeight - barHeight
                drawRect(
                    ChartTeal,
                    topLeft = Offset(slotCenter - barWidth / 2, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                )

                val measured = textMeasurer.measure(bar.displayValue, style = TextStyle(fontSize = 9.sp))
                drawText(
                    textMeasurer, bar.displayValue,
                    topLeft = Offset(slotCenter - measured.size.width / 2, (top - measured.size.height - 4f).coerceAtLeast(0f)),
                    style = TextStyle(fontSize = 9.sp, color = ChartTeal),
                )

                rotate(degrees = 40f, pivot = Offset(slotCenter, topPad + plotHeight + 6f)) {
                    drawText(
                        textMeasurer, bar.label,
                        topLeft = Offset(slotCenter, topPad + plotHeight + 6f),
                        style = TextStyle(fontSize = 9.sp, color = labelColor),
                    )
                }
            }
        }
    }
}
