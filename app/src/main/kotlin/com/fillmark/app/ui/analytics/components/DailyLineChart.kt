package com.fillmark.app.ui.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.domain.ChartPoint
import com.fillmark.app.domain.DailyGraphMode
import com.fillmark.app.domain.DailyLineChartData
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkNavy700
import com.fillmark.app.ui.theme.FillmarkNavy900
import kotlin.math.abs

@Composable
fun DailyLineChart(
    data: DailyLineChartData,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember(data) { mutableStateOf<ChartPoint?>(null) }

    val trackColor = FillmarkNavy700
    val targetLineColor = Color(0x80A5B9CC)
    val paceLineColor = Color(0x9938BDF8)
    val labelColorInt = Color(0xFF8094A8).toArgb()
    val navy900Int = FillmarkNavy900.toArgb()
    val whiteInt = android.graphics.Color.WHITE
    val cyanInt = FillmarkCyan.toArgb()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
            .pointerInput(data) {
                detectTapGestures(
                    onTap = { offset ->
                        val paddingStart = 20.dp.toPx()
                        val paddingEnd = 20.dp.toPx()
                        val plotWidth = size.width - paddingStart - paddingEnd
                        if (plotWidth > 0 && data.points.isNotEmpty()) {
                            val touchedHour = ((offset.x - paddingStart) / plotWidth * 24f).coerceIn(0f, 24f)
                            val closest = data.points.minByOrNull { abs(it.hourOfDay - touchedHour) }
                            selectedPoint = if (selectedPoint == closest) null else closest
                        }
                    }
                )
            }
    ) {
        val paddingStart = 20.dp.toPx()
        val paddingEnd = 20.dp.toPx()
        val paddingTop = 16.dp.toPx()
        val paddingBottom = 26.dp.toPx()

        val plotLeft = paddingStart
        val plotRight = size.width - paddingEnd
        val plotTop = paddingTop
        val plotBottom = size.height - paddingBottom
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        if (plotWidth <= 0 || plotHeight <= 0) return@Canvas

        val maxPointVal = data.points.maxOfOrNull { it.value } ?: 0.0
        val maxRefVal = when (data.mode) {
            DailyGraphMode.CUMULATIVE -> if (data.hasTarget) data.targetVolumeMl else 0.0
            DailyGraphMode.RATE -> if (data.hasTarget) data.targetRatePerHour else 0.0
        }
        val defaultMin = if (data.mode == DailyGraphMode.CUMULATIVE) 100.0 else 50.0
        val maxY = maxOf(maxPointVal, maxRefVal, defaultMin) * 1.18

        fun hourToX(hour: Float): Float {
            return plotLeft + (hour.coerceIn(0f, 24f) / 24f) * plotWidth
        }

        fun valToY(v: Double): Float {
            val fraction = (v / maxY).toFloat().coerceIn(0f, 1f)
            return plotBottom - fraction * plotHeight
        }

        // 1. Horizontal background baseline and faint gridlines
        drawLine(
            color = trackColor,
            start = Offset(plotLeft, plotBottom),
            end = Offset(plotRight, plotBottom),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Bottom 24-hour time labels
        val timeLabels = listOf(
            0f to "12a",
            6f to "6a",
            12f to "12p",
            18f to "6p",
            24f to "12a"
        )
        val labelPaint = android.graphics.Paint().apply {
            color = labelColorInt
            textSize = 10.5.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        timeLabels.forEach { (hour, text) ->
            val x = hourToX(hour)
            drawContext.canvas.nativeCanvas.drawText(
                text,
                x,
                size.height - 4.dp.toPx(),
                labelPaint
            )
        }

        val refLineTextPaint = android.graphics.Paint().apply {
            color = Color(0xCC8CA3BA).toArgb()
            textSize = 9.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }

        // 3. Reference lines
        if (data.mode == DailyGraphMode.CUMULATIVE && data.hasTarget) {
            // Horizontal reference line for target consumption
            val targetY = valToY(data.targetVolumeMl)
            drawLine(
                color = targetLineColor,
                start = Offset(plotLeft, targetY),
                end = Offset(plotRight, targetY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
            )

            // Horizontal target label
            val formattedTarget = VolumeConverter.formatVolume(data.targetVolumeMl, data.displayUnit)
            drawContext.canvas.nativeCanvas.drawText(
                "Target $formattedTarget",
                plotRight - 4.dp.toPx(),
                (targetY - 4.dp.toPx()).coerceAtLeast(plotTop + 10.dp.toPx()),
                refLineTextPaint
            )

            // Sloped reference line for target consumption rate from wake to sleep
            val startHour = data.wakingHoursStart ?: 0.0f
            val endHour = data.wakingHoursEnd ?: 24.0f
            val startX = hourToX(startHour)
            val startY = valToY(0.0)
            val endX = hourToX(minOf(24.0f, endHour))
            val targetAtEnd = if (endHour > 24.0f && (endHour - startHour) > 0f) {
                data.targetVolumeMl * ((24.0f - startHour) / (endHour - startHour))
            } else {
                data.targetVolumeMl
            }
            val endY = valToY(targetAtEnd)

            drawLine(
                color = paceLineColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
            )
        } else if (data.mode == DailyGraphMode.RATE && data.hasTarget) {
            // Horizontal reference line for target consumption rate
            val rateY = valToY(data.targetRatePerHour)
            drawLine(
                color = targetLineColor,
                start = Offset(plotLeft, rateY),
                end = Offset(plotRight, rateY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
            )

            val formattedRate = "${VolumeConverter.formatVolume(data.targetRatePerHour, data.displayUnit)}/h"
            drawContext.canvas.nativeCanvas.drawText(
                "Target $formattedRate",
                plotRight - 4.dp.toPx(),
                (rateY - 4.dp.toPx()).coerceAtLeast(plotTop + 10.dp.toPx()),
                refLineTextPaint
            )
        }

        // 4. Sloped line connecting points and gradient fill
        val points = data.points
        val canDrawLine = points.isNotEmpty() && (data.mode == DailyGraphMode.CUMULATIVE || data.hasEntries)

        if (canDrawLine) {
            val strokePath = Path()
            val firstX = hourToX(points.first().hourOfDay)
            val firstY = valToY(points.first().value)
            strokePath.moveTo(firstX, firstY)

            for (i in 1 until points.size) {
                val pt = points[i]
                val px = hourToX(pt.hourOfDay)
                val py = valToY(pt.value)
                strokePath.lineTo(px, py)
            }

            // Filled gradient under the sloped line
            val lastX = hourToX(points.last().hourOfDay)
            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(lastX, plotBottom)
                lineTo(firstX, plotBottom)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        FillmarkCyan.copy(alpha = 0.25f),
                        FillmarkCyan.copy(alpha = 0.01f)
                    ),
                    startY = plotTop,
                    endY = plotBottom
                )
            )

            // Sloped line stroke
            drawPath(
                path = strokePath,
                color = FillmarkCyan,
                style = Stroke(
                    width = 2.75.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw point dots
            points.forEach { pt ->
                val px = hourToX(pt.hourOfDay)
                val py = valToY(pt.value)

                // Outer halo
                drawCircle(
                    color = Color(0x3320D2DE),
                    radius = 5.5.dp.toPx(),
                    center = Offset(px, py)
                )
                // Solid cyan circle
                drawCircle(
                    color = FillmarkCyan,
                    radius = 3.5.dp.toPx(),
                    center = Offset(px, py)
                )
                // Center white dot
                drawCircle(
                    color = Color.White,
                    radius = 1.5.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // 5. Empty state label if no entries yet
        if (!data.hasEntries) {
            val emptyPaint = android.graphics.Paint().apply {
                color = Color(0xFF6B7C8E).toArgb()
                textSize = 12.sp.toPx()
                typeface = android.graphics.Typeface.DEFAULT
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "No drink entries recorded yet today",
                (plotLeft + plotRight) / 2f,
                (plotTop + plotBottom) / 2f,
                emptyPaint
            )
        }

        // 6. Value badge: active selected point or latest entry point
        val activePoint = selectedPoint ?: if (data.hasEntries) points.lastOrNull() else null
        if (activePoint != null && (data.mode == DailyGraphMode.CUMULATIVE || data.hasEntries)) {
            val px = hourToX(activePoint.hourOfDay)
            val py = valToY(activePoint.value)

            // Highlight ring for active point
            drawCircle(
                color = Color(0x6620D2DE),
                radius = 8.dp.toPx(),
                center = Offset(px, py)
            )

            // Badge pill
            val badgeText = activePoint.displayValue
            val badgePaint = android.graphics.Paint().apply {
                color = navy900Int
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val textWidth = badgePaint.measureText(badgeText)
            val badgeWidth = textWidth + 14.dp.toPx()
            val badgeHeight = 18.dp.toPx()
            val badgeCorner = CornerRadius(9.dp.toPx(), 9.dp.toPx())

            val badgeLeft = (px - badgeWidth / 2f).coerceIn(plotLeft, plotRight - badgeWidth)
            val badgeTop = (py - badgeHeight - 6.dp.toPx()).coerceIn(plotTop, plotBottom - badgeHeight)

            drawRoundRect(
                color = FillmarkCyan,
                topLeft = Offset(badgeLeft, badgeTop),
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = badgeCorner
            )

            drawContext.canvas.nativeCanvas.drawText(
                badgeText,
                badgeLeft + badgeWidth / 2f,
                badgeTop + badgeHeight * 0.72f,
                badgePaint
            )
        }
    }
}

