package com.fillmark.app.ui.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.ui.analytics.ChartBarData
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun BarChart(
    bars: List<ChartBarData>,
    modifier: Modifier = Modifier,
    displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    selectedIndex: Int? = null,
    onBarClick: ((Int, ChartBarData) -> Unit)? = null
) {
    if (bars.isEmpty()) return

    val selectedBar = selectedIndex?.let { bars.getOrNull(it) }
    val chartDescription = if (selectedBar != null) {
        val barLabel = selectedBar.label.ifEmpty {
            selectedBar.dateIso ?: stringResource(R.string.analytics_period_fallback)
        }
        val formattedVolume = VolumeConverter.formatVolume(selectedBar.valueMl, displayUnit)
        stringResource(R.string.analytics_barchart_selected_a11y, barLabel, formattedVolume)
    } else {
        pluralStringResource(R.plurals.analytics_barchart_a11y, bars.size, bars.size)
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColorInt = MaterialTheme.colorScheme.onPrimary.toArgb()
    val secondaryAccent = MaterialTheme.colorScheme.tertiary
    val targetLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val labelColorInt = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val selectionStrokeColor = MaterialTheme.colorScheme.onSurface

    val maxVolume = max(
        bars.maxOfOrNull { it.valueMl } ?: 1.0,
        bars.maxOfOrNull { it.targetMl } ?: 1.0
    ) * 1.18 // 18% headroom for badges

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(205.dp)
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
            .semantics { contentDescription = chartDescription }
            .then(
                if (onBarClick != null) {
                    Modifier.pointerInput(bars) {
                        detectTapGestures { offset ->
                            val chartWidth = size.width.toFloat()
                            if (chartWidth > 0f && bars.isNotEmpty()) {
                                val slotWidth = chartWidth / bars.size
                                val idx = (offset.x / slotWidth).toInt().coerceIn(0, bars.lastIndex)
                                onBarClick(idx, bars[idx])
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val chartHeight = size.height - 28.dp.toPx()
        val chartWidth = size.width
        val barCount = bars.size
        val barSpacing = (chartWidth / barCount) * 0.22f
        val barWidth = (chartWidth / barCount) - barSpacing
        val cornerPx = minOf(10.dp.toPx(), barWidth / 2f)
        val cornerRadius = CornerRadius(cornerPx, cornerPx)
        val badgeWidth = 34.dp.toPx()
        val showBadges = barWidth >= badgeWidth

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
                            primaryColor,
                            secondaryAccent
                        ),
                        startY = top,
                        endY = chartHeight
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )
            }

            if (selectedIndex == index) {
                drawRoundRect(
                    color = selectionStrokeColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, chartHeight),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            if (hasData && showBadges) {
                val pct = if (bar.targetMl > 0) {
                    ((bar.valueMl / bar.targetMl) * 100).roundToInt()
                } else 100

                val isHighOrCurrent = pct >= 50 || bar.isCurrent
                val badgeBgColor = if (isHighOrCurrent) primaryColor else Color(0xFFD97706)
                val badgeHeight = 18.dp.toPx()
                val badgeCorner = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                val badgeCenterX = left + barWidth / 2f
                val badgeLeft = badgeCenterX - badgeWidth / 2f

                val badgeTop = (top - badgeHeight / 2f).coerceIn(2.dp.toPx(), chartHeight - badgeHeight - 2.dp.toPx())

                drawRoundRect(
                    color = badgeBgColor,
                    topLeft = Offset(badgeLeft, badgeTop),
                    size = Size(badgeWidth, badgeHeight),
                    cornerRadius = badgeCorner
                )

                val badgeTextPaint = android.graphics.Paint().apply {
                    color = onPrimaryColorInt
                    textSize = 11.sp.toPx()
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

            // Bottom Day Label
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
