package com.fillmark.app.ui.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.domain.PacingResult
import com.fillmark.app.domain.PacingStatus
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkIndigo
import com.fillmark.app.ui.theme.FillmarkNavy900
import com.fillmark.app.ui.theme.FillmarkNavyTitle
import com.fillmark.app.ui.theme.FillmarkSlateMuted
import com.fillmark.app.ui.theme.PaceAheadBg
import com.fillmark.app.ui.theme.PaceAheadBorder
import com.fillmark.app.ui.theme.PaceAheadIcon
import com.fillmark.app.ui.theme.PaceAheadText
import com.fillmark.app.ui.theme.PaceBehindBg
import com.fillmark.app.ui.theme.PaceBehindBorder
import com.fillmark.app.ui.theme.PaceBehindIcon
import com.fillmark.app.ui.theme.PaceBehindText
import com.fillmark.app.ui.theme.PaceOnTrackBg
import com.fillmark.app.ui.theme.PaceOnTrackBorder
import com.fillmark.app.ui.theme.PaceOnTrackIcon
import com.fillmark.app.ui.theme.PaceOnTrackText
import kotlin.math.roundToInt

@Composable
fun FluidProgressMeter(
    effectiveVolumeMl: Double,
    targetMl: Double,
    displayUnit: DisplayUnit,
    pacingResult: PacingResult?,
    modifier: Modifier = Modifier
) {
    val hasTarget = targetMl > 0
    val progressRatio = if (hasTarget) (effectiveVolumeMl / targetMl).toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio.coerceIn(0f, 2f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "ProgressAnimation"
    )

    val targetPacingRatio = (pacingResult?.targetPacingPercent ?: 0f) / 100f
    val animatedPacing by animateFloatAsState(
        targetValue = targetPacingRatio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "PacingAnimation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(246.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val strokeWidth = 14.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val centerOffset = Offset(size.width / 2, size.height / 2)
                val innerRadius = radius - strokeWidth / 2

                // Subtle water wave / gradient graphic behind the metric texts
                val clipCircle = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            centerOffset.x - innerRadius,
                            centerOffset.y - innerRadius,
                            centerOffset.x + innerRadius,
                            centerOffset.y + innerRadius
                        )
                    )
                }

                clipPath(clipCircle) {
                    val waveWidth = innerRadius * 2
                    val waveHeight = innerRadius * 2
                    val waveBaseY = centerOffset.y + innerRadius * 0.15f

                    // Secondary subtle wave (background)
                    val backWave = Path().apply {
                        moveTo(centerOffset.x - innerRadius, waveBaseY)
                        cubicTo(
                            centerOffset.x - innerRadius * 0.45f, waveBaseY - 18.dp.toPx(),
                            centerOffset.x + innerRadius * 0.1f, waveBaseY + 14.dp.toPx(),
                            centerOffset.x + innerRadius, waveBaseY - 8.dp.toPx()
                        )
                        lineTo(centerOffset.x + innerRadius, centerOffset.y + innerRadius)
                        lineTo(centerOffset.x - innerRadius, centerOffset.y + innerRadius)
                        close()
                    }
                    drawPath(
                        path = backWave,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                FillmarkCyan.copy(alpha = 0.18f),
                                FillmarkCyan.copy(alpha = 0.04f)
                            ),
                            startY = waveBaseY - 20.dp.toPx(),
                            endY = centerOffset.y + innerRadius
                        )
                    )

                    // Primary subtle wave (foreground)
                    val frontWave = Path().apply {
                        moveTo(centerOffset.x - innerRadius, waveBaseY + 8.dp.toPx())
                        cubicTo(
                            centerOffset.x - innerRadius * 0.35f, waveBaseY + 22.dp.toPx(),
                            centerOffset.x + innerRadius * 0.35f, waveBaseY - 14.dp.toPx(),
                            centerOffset.x + innerRadius, waveBaseY + 6.dp.toPx()
                        )
                        lineTo(centerOffset.x + innerRadius, centerOffset.y + innerRadius)
                        lineTo(centerOffset.x - innerRadius, centerOffset.y + innerRadius)
                        close()
                    }
                    drawPath(
                        path = frontWave,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                FillmarkCyan.copy(alpha = 0.30f),
                                FillmarkCyan.copy(alpha = 0.08f)
                            ),
                            startY = waveBaseY - 10.dp.toPx(),
                            endY = centerOffset.y + innerRadius
                        )
                    )
                }

                // Outer circular track (subtle background ring)
                drawArc(
                    color = Color(0xFFE2EAF1),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                if (hasTarget) {
                    // Dual-progress gauge layer 1: Deep Indigo pacing target arc
                    if (animatedPacing > 0.01f) {
                        val pacingSweep = (animatedPacing * 360f).coerceIn(4f, 360f)
                        drawArc(
                            color = FillmarkIndigo,
                            startAngle = -90f,
                            sweepAngle = pacingSweep,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Dual-progress gauge layer 2: Bright Cyan/Teal logged intake arc
                    if (animatedProgress > 0.005f) {
                        val intakeSweep = (animatedProgress.coerceAtMost(1f) * 360f).coerceIn(4f, 360f)
                        drawArc(
                            color = FillmarkCyan,
                            startAngle = -90f,
                            sweepAngle = intakeSweep,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Overflow arc if intake exceeds 100%
                    if (animatedProgress > 1f) {
                        val overflowSweep = ((animatedProgress - 1f).coerceIn(0f, 1f) * 360f)
                        drawArc(
                            color = Color(0xFF00E5FF),
                            startAngle = -90f,
                            sweepAngle = overflowSweep,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Central Metric Texts
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (hasTarget) {
                    val percent = (progressRatio * 100).roundToInt()
                    Text(
                        text = VolumeConverter.formatNumberOnly(effectiveVolumeMl, displayUnit),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = FillmarkNavyTitle
                    )
                    Text(
                        text = displayUnit.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FillmarkNavyTitle.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$percent% of ${VolumeConverter.formatVolume(targetMl, displayUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = FillmarkSlateMuted
                    )
                } else {
                    Text(
                        text = VolumeConverter.formatNumberOnly(effectiveVolumeMl, displayUnit),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = FillmarkNavyTitle
                    )
                    Text(
                        text = displayUnit.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FillmarkNavyTitle
                    )
                }
            }
        }

        // Low-Profile, Tinted Pace Status Notification Pill
        if (hasTarget && pacingResult != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val (bgColor, borderColor, textColor, iconColor, iconVector) = when (pacingResult.status) {
                PacingStatus.BEHIND -> PaceStatusStyle(
                    bg = PaceBehindBg,
                    border = PaceBehindBorder,
                    text = PaceBehindText,
                    iconTint = PaceBehindIcon,
                    icon = Icons.Filled.Error
                )
                PacingStatus.ON_TRACK -> PaceStatusStyle(
                    bg = PaceOnTrackBg,
                    border = PaceOnTrackBorder,
                    text = PaceOnTrackText,
                    iconTint = PaceOnTrackIcon,
                    icon = Icons.Filled.CheckCircle
                )
                PacingStatus.AHEAD -> PaceStatusStyle(
                    bg = PaceAheadBg,
                    border = PaceAheadBorder,
                    text = PaceAheadText,
                    iconTint = PaceAheadIcon,
                    icon = Icons.Filled.CheckCircle
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = bgColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = "Pace Status",
                        tint = iconColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pace: ${pacingResult.statusText}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
            }
        }
    }
}

private data class PaceStatusStyle(
    val bg: Color,
    val border: Color,
    val text: Color,
    val iconTint: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
