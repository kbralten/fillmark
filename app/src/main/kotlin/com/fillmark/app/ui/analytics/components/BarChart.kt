package com.fillmark.app.ui.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fillmark.app.ui.analytics.ChartBarData
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkNavy700
import com.fillmark.app.ui.theme.FillmarkNavy900
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun BarChart(
    bars: List<ChartBarData>,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return

    val trackColor = FillmarkNavy700
    val targetLineColor = Color(0x66A5B9CC)
    val labelColorInt = Color(0xFF8094A8).toArgb()
    val badgeNavyColorInt = FillmarkNavy900.toArgb()

    val maxVolume = max(
        bars.maxOfOrNull { it.valueMl } ?: 1.0,
        bars.maxOfOrNull { it.targetMl } ?: 1.0
    ) * 1.18 // 18% headroom for badges

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(205.dp)
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
    ) {
        val chartHeight = size.height - 28.dp.toPx()
        val chartWidth = size.width
        val barCount = bars.size
        val barSpacing = (chartWidth / barCount) * 0.22f
        val barWidth = (chartWidth / barCount) - barSpacing
        val cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())

        // 1. Subtle horizontal dashed target line
        val avgTarget = bars.map { it.targetMl }.average()
        if (avgTarget > 0) {
            val targetY = chartHeight - ((avgTarget / maxVolume).toFloat() * chartHeight).coerceIn(10.dp.toPx(), chartHeight - 10.dp.toPx())
            drawLine(
                color = targetLineColor,
                start = Offset(0f, targetY),
                end = Offset(chartWidth, targetY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()), 0f)
            )
        }

        // 2. Bar tracks and data fills
        bars.forEachIndexed { index, bar ->
            val left = index * (barWidth + barSpacing) + (barSpacing / 2f)
            val hasData = bar.valueMl > 0
            val barHeight = if (hasData) {
                ((bar.valueMl / maxVolume).toFloat() * chartHeight).coerceIn(10.dp.toPx(), chartHeight)
            } else 0f
            val top = chartHeight - barHeight

            // Background full-height rounded track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, chartHeight),
                cornerRadius = cornerRadius
            )

            // Active Intake Fill Bar
            if (hasData) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            FillmarkCyan,
                            Color(0xFF0EA5E9)
                        ),
                        startY = top,
                        endY = chartHeight
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )

                // Accent percentage badge for days featuring historical data
                val pct = if (bar.targetMl > 0) {
                    ((bar.valueMl / bar.targetMl) * 100).roundToInt()
                } else 100

                val isHighOrCurrent = pct >= 50 || bar.isCurrent
                val badgeBgColor = if (isHighOrCurrent) FillmarkCyan else Color(0xFFF5A67B)
                val badgeWidth = 28.dp.toPx()
                val badgeHeight = 15.dp.toPx()
                val badgeCorner = CornerRadius(7.5.dp.toPx(), 7.5.dp.toPx())
                val badgeCenterX = left + barWidth / 2f
                val badgeLeft = badgeCenterX - badgeWidth / 2f

                // Position badge right at the fill boundary or slightly offset
                val badgeTop = (top - badgeHeight / 2f).coerceIn(2.dp.toPx(), chartHeight - badgeHeight - 2.dp.toPx())

                drawRoundRect(
                    color = badgeBgColor,
                    topLeft = Offset(badgeLeft, badgeTop),
                    size = Size(badgeWidth, badgeHeight),
                    cornerRadius = badgeCorner
                )

                // Draw percentage text inside badge
                val badgeTextPaint = android.graphics.Paint().apply {
                    color = badgeNavyColorInt
                    textSize = 8.5.sp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "$pct%",
                    badgeCenterX,
                    badgeTop + badgeHeight * 0.74f,
                    badgeTextPaint
                )
            }

            // Bottom Day Label (e.g., Thu, Fri, Sat...)
            if (bar.label.isNotEmpty()) {
                val labelPaint = android.graphics.Paint().apply {
                    color = labelColorInt
                    textSize = 11.sp.toPx()
                    typeface = if (bar.isCurrent) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    bar.label,
                    left + barWidth / 2f,
                    size.height - 4.dp.toPx(),
                    labelPaint
                )
            }
        }
    }
}
