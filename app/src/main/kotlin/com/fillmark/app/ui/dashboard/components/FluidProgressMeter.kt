package com.fillmark.app.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.domain.GoalMilestoneTracker
import com.fillmark.app.domain.PacingResult
import com.fillmark.app.domain.PacingStatus
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkIndigo
import com.fillmark.app.ui.theme.PaceAheadBg
import com.fillmark.app.ui.theme.PaceAheadBgDark
import com.fillmark.app.ui.theme.PaceAheadBorder
import com.fillmark.app.ui.theme.PaceAheadBorderDark
import com.fillmark.app.ui.theme.PaceAheadIcon
import com.fillmark.app.ui.theme.PaceAheadIconDark
import com.fillmark.app.ui.theme.PaceAheadText
import com.fillmark.app.ui.theme.PaceAheadTextDark
import com.fillmark.app.ui.theme.PaceBehindBg
import com.fillmark.app.ui.theme.PaceBehindBgDark
import com.fillmark.app.ui.theme.PaceBehindBorder
import com.fillmark.app.ui.theme.PaceBehindBorderDark
import com.fillmark.app.ui.theme.PaceBehindIcon
import com.fillmark.app.ui.theme.PaceBehindIconDark
import com.fillmark.app.ui.theme.PaceBehindText
import com.fillmark.app.ui.theme.PaceBehindTextDark
import com.fillmark.app.ui.theme.PaceOnTrackBg
import com.fillmark.app.ui.theme.PaceOnTrackBgDark
import com.fillmark.app.ui.theme.PaceOnTrackBorder
import com.fillmark.app.ui.theme.PaceOnTrackBorderDark
import com.fillmark.app.ui.theme.PaceOnTrackIcon
import com.fillmark.app.ui.theme.PaceOnTrackIconDark
import com.fillmark.app.ui.theme.PaceOnTrackText
import com.fillmark.app.ui.theme.PaceOnTrackTextDark
import kotlin.math.roundToInt

@Composable
fun FluidProgressMeter(
    effectiveVolumeMl: Double,
    targetMl: Double,
    displayUnit: DisplayUnit,
    pacingResult: PacingResult?,
    modifier: Modifier = Modifier,
    isTargetOverridden: Boolean = false,
    onTargetClick: (() -> Unit)? = null
) {
    val hasTarget = targetMl > 0
    val goalReached = GoalMilestoneTracker.isGoalReached(effectiveVolumeMl, targetMl)
    var previousVolumeMl by remember { mutableDoubleStateOf(effectiveVolumeMl) }
    val celebrationPulse = remember { Animatable(0f) }

    LaunchedEffect(effectiveVolumeMl, targetMl) {
        if (GoalMilestoneTracker.didCrossDailyGoal(previousVolumeMl, effectiveVolumeMl, targetMl)) {
            celebrationPulse.snapTo(0f)
            celebrationPulse.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
            celebrationPulse.snapTo(0f)
        }
        previousVolumeMl = effectiveVolumeMl
    }

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

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val trackColor = if (isDark) Color(0xFF1A2F44) else Color(0xFFE2EAF1)
    val pacingTrackColor = if (isDark) Color(0xFF2B587E) else FillmarkIndigo
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val consumedFormatted = VolumeConverter.formatVolume(effectiveVolumeMl, displayUnit)
    val percent = if (hasTarget) (progressRatio * 100).roundToInt() else 0
    val gaugeContentDescription = if (hasTarget) {
        val targetFormatted = VolumeConverter.formatVolume(targetMl, displayUnit)
        if (pacingResult != null) {
            stringResource(
                R.string.meter_a11y_with_target_and_pace,
                consumedFormatted,
                targetFormatted,
                percent,
                pacingResult.statusText
            )
        } else {
            stringResource(R.string.meter_a11y_with_target, consumedFormatted, targetFormatted, percent)
        }
    } else {
        stringResource(R.string.meter_a11y_no_target, consumedFormatted)
    }

    val density = LocalDensity.current
    val gaugeScale = density.fontScale.coerceIn(1f, 1.18f)
    val ringSize = 246.dp * gaugeScale
    val displayStyle = MaterialTheme.typography.displayMedium
    val numberFontSize = displayStyle.fontSize * (density.fontScale.coerceAtMost(1.3f) / density.fontScale)
    val subtitleStyle = MaterialTheme.typography.bodyMedium
    val subtitleFontSize = subtitleStyle.fontSize * (density.fontScale.coerceAtMost(1.35f) / density.fontScale)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .semantics { contentDescription = gaugeContentDescription }
            ) {
                val strokeWidth = 14.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val centerOffset = Offset(size.width / 2, size.height / 2)
                val innerRadius = radius - strokeWidth / 2

                // Subtle water wave / gradient graphic behind the metric texts
                val clipCircle = Path().apply {
                    addOval(
                        Rect(
                            centerOffset.x - innerRadius,
                            centerOffset.y - innerRadius,
                            centerOffset.x + innerRadius,
                            centerOffset.y + innerRadius
                        )
                    )
                }

                clipPath(clipCircle) {
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
                    color = trackColor,
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
                            color = pacingTrackColor,
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

                    // One-shot celebration burst ring when crossing 100%
                    val pulse = celebrationPulse.value
                    if (pulse > 0f && pulse < 1f) {
                        val burstRadius = radius + (14.dp.toPx() * pulse)
                        drawCircle(
                            color = FillmarkCyan.copy(alpha = (1f - pulse) * 0.55f),
                            radius = burstRadius,
                            center = centerOffset,
                            style = Stroke(width = (6.dp.toPx() * (1f - pulse)).coerceAtLeast(1f))
                        )
                    }
                }
            }

            // Central Metric Texts
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (hasTarget) {
                    Text(
                        text = VolumeConverter.formatNumberOnly(effectiveVolumeMl, displayUnit),
                        style = displayStyle,
                        fontSize = numberFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        color = primaryTextColor
                    )
                    Text(
                        text = displayUnit.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = primaryTextColor.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = if (onTargetClick != null) {
                            Modifier
                                .clickable(onClick = onTargetClick)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = stringResource(
                                R.string.meter_percent_of_target,
                                percent,
                                VolumeConverter.formatVolume(targetMl, displayUnit)
                            ),
                            style = subtitleStyle,
                            fontSize = subtitleFontSize,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = mutedTextColor
                        )
                        if (onTargetClick != null) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.meter_adjust_target_cd),
                                tint = mutedTextColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    if (isTargetOverridden) {
                        Text(
                            text = stringResource(R.string.meter_today_only),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = VolumeConverter.formatNumberOnly(effectiveVolumeMl, displayUnit),
                        style = displayStyle,
                        fontSize = numberFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        color = primaryTextColor
                    )
                    Text(
                        text = displayUnit.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = primaryTextColor
                    )
                    if (onTargetClick != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable(onClick = onTargetClick)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.meter_set_today_goal),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.meter_set_today_goal),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = goalReached,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isDark) PaceAheadBgDark else PaceAheadBg,
                border = BorderStroke(1.dp, if (isDark) PaceAheadBorderDark else PaceAheadBorder),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = if (isDark) PaceAheadIconDark else PaceAheadIcon,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.meter_goal_reached_badge),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) PaceAheadTextDark else PaceAheadText
                    )
                }
            }
        }

        // Low-Profile, Tinted Pace Status Notification Pill
        if (hasTarget && pacingResult != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val (bgColor, borderColor, textColor, iconColor, iconVector) = when (pacingResult.status) {
                PacingStatus.BEHIND -> PaceStatusStyle(
                    bg = if (isDark) PaceBehindBgDark else PaceBehindBg,
                    border = if (isDark) PaceBehindBorderDark else PaceBehindBorder,
                    text = if (isDark) PaceBehindTextDark else PaceBehindText,
                    iconTint = if (isDark) PaceBehindIconDark else PaceBehindIcon,
                    icon = Icons.Filled.Error
                )
                PacingStatus.ON_TRACK -> PaceStatusStyle(
                    bg = if (isDark) PaceOnTrackBgDark else PaceOnTrackBg,
                    border = if (isDark) PaceOnTrackBorderDark else PaceOnTrackBorder,
                    text = if (isDark) PaceOnTrackTextDark else PaceOnTrackText,
                    iconTint = if (isDark) PaceOnTrackIconDark else PaceOnTrackIcon,
                    icon = Icons.Filled.CheckCircle
                )
                PacingStatus.AHEAD -> PaceStatusStyle(
                    bg = if (isDark) PaceAheadBgDark else PaceAheadBg,
                    border = if (isDark) PaceAheadBorderDark else PaceAheadBorder,
                    text = if (isDark) PaceAheadTextDark else PaceAheadText,
                    iconTint = if (isDark) PaceAheadIconDark else PaceAheadIcon,
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
                        contentDescription = stringResource(R.string.meter_pace_status_cd),
                        tint = iconColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.meter_pace_label, pacingResult.statusText),
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
