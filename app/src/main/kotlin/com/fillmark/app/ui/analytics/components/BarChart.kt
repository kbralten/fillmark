package com.fillmark.app.ui.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.fillmark.app.ui.analytics.ChartBarData
import kotlin.math.max

@Composable
fun BarChart(
    bars: List<ChartBarData>,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val currentDayColor = MaterialTheme.colorScheme.tertiary
    val targetLineColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    val trackBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()

    val maxVolume = max(
        bars.maxOfOrNull { it.valueMl } ?: 1.0,
        bars.maxOfOrNull { it.targetMl } ?: 1.0
    ) * 1.15 // 15% headroom

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
    ) {
        val chartHeight = size.height - 30.dp.toPx()
        val chartWidth = size.width
        val barCount = bars.size
        val barSpacing = (chartWidth / barCount) * 0.25f
        val barWidth = (chartWidth / barCount) - barSpacing

        // Draw target line only if target is set and > 0
        val avgTarget = bars.map { it.targetMl }.average()
        if (avgTarget > 0) {
            val targetY = chartHeight - ((avgTarget / maxVolume).toFloat() * chartHeight)
            drawLine(
                color = targetLineColor,
                start = Offset(0f, targetY),
                end = Offset(chartWidth, targetY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )
        }

        bars.forEachIndexed { index, bar ->
            val left = index * (barWidth + barSpacing) + (barSpacing / 2f)
            val barHeight = ((bar.valueMl / maxVolume).toFloat() * chartHeight).coerceAtLeast(4.dp.toPx())
            val top = chartHeight - barHeight

            // Track background bar
            drawRoundRect(
                color = trackBgColor,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, chartHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Value bar
            val barColor = if (bar.isCurrent) currentDayColor else primaryColor
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Bottom Label
            if (bar.label.isNotEmpty()) {
                val paint = android.graphics.Paint().apply {
                    color = labelColor
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    bar.label,
                    left + barWidth / 2f,
                    size.height - 4.dp.toPx(),
                    paint
                )
            }
        }
    }
}

