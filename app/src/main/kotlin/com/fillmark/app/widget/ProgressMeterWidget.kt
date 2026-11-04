package com.fillmark.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.fillmark.app.MainActivity
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.domain.PacingCalculator
import com.fillmark.app.domain.PacingResult
import com.fillmark.app.domain.PacingStatus
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

class ProgressMeterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = FillmarkDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)

        val today = LocalDate.now()
        val startMillis = TimeUtils.startOfDayMillis(today)
        val endMillis = TimeUtils.endOfDayMillis(today)
        val dateIso = TimeUtils.currentDateIso()

        val initialRecords = database.hydrationDao().getRecordsBetween(startMillis, endMillis)
        val initialTarget = database.dailyTargetDao().getTargetForDate(dateIso)
        val initialPrefs = prefsRepo.preferencesFlow.first()

        provideContent {
            GlanceTheme {
                val todayRecords by remember {
                    database.hydrationDao().observeRecordsBetween(startMillis, endMillis)
                }.collectAsState(initial = initialRecords)

                val targetEntity by remember {
                    database.dailyTargetDao().observeTargetForDate(dateIso)
                }.collectAsState(initial = initialTarget)

                val prefs by remember {
                    prefsRepo.preferencesFlow
                }.collectAsState(initial = initialPrefs)

                val effectiveTotalMl = todayRecords.sumOf { it.effectiveVolumeMl }
                val targetMl = targetEntity?.targetMl ?: prefs.defaultDailyTargetMl
                val hasTarget = targetMl > 0
                val rawRatio = if (hasTarget) (effectiveTotalMl / targetMl).toFloat() else 0f
                val percentDisplay = (rawRatio * 100).roundToInt()

                val pacing = if (hasTarget && prefs.wakingPacingEnabled) {
                    PacingCalculator.evaluatePacing(
                        currentTime = LocalTime.now(),
                        wakeTimeString = prefs.wakingHoursStart,
                        sleepTimeString = prefs.wakingHoursEnd,
                        currentVolumeMl = effectiveTotalMl,
                        dailyTargetMl = targetMl,
                        displayUnit = prefs.displayUnit
                    )
                } else {
                    null
                }

                val paceRatio = if (pacing != null) (pacing.targetPacingPercent / 100f) else null
                val progressBitmap = remember(rawRatio, paceRatio) {
                    WidgetProgressBarGenerator.createProgressBarBitmap(
                        context = context,
                        progress = rawRatio,
                        paceRatio = paceRatio,
                        widthDp = 280,
                        heightDp = 12
                    )
                }

                if (!hasTarget) {
                    // No Target Set: Big Numbers layout
                    NoTargetProgressMeterContent(
                        consumedText = VolumeConverter.formatVolume(effectiveTotalMl, prefs.displayUnit)
                    )
                } else {
                    // Target Set: Progress meter with On Track dot
                    ProgressMeterContent(
                        consumedText = VolumeConverter.formatVolume(effectiveTotalMl, prefs.displayUnit),
                        targetText = VolumeConverter.formatVolume(targetMl, prefs.displayUnit),
                        progressBitmap = progressBitmap,
                        percentDisplay = percentDisplay,
                        pacing = pacing
                    )
                }
            }
        }
    }

    @Composable
    private fun NoTargetProgressMeterContent(consumedText: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "Fillmark",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "Today's Intake",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = consumedText,
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = "Total consumed (no daily target)",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }

    @Composable
    private fun ProgressMeterContent(
        consumedText: String,
        targetText: String,
        progressBitmap: android.graphics.Bitmap,
        percentDisplay: Int,
        pacing: PacingResult?
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "Fillmark",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "$percentDisplay%",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (percentDisplay >= 100) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Progress bar bitmap with on-track pacing marker dot
            Image(
                provider = ImageProvider(progressBitmap),
                contentDescription = "Progress: $percentDisplay%",
                modifier = GlanceModifier.fillMaxWidth().height(12.dp)
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "$consumedText / $targetText",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())

                // On-track indicator dot and status
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    val dotColor = when (pacing?.status) {
                        PacingStatus.AHEAD -> GlanceTheme.colors.primary
                        PacingStatus.ON_TRACK -> GlanceTheme.colors.tertiary
                        PacingStatus.BEHIND -> GlanceTheme.colors.error
                        null -> GlanceTheme.colors.primary
                    }

                    Box(
                        modifier = GlanceModifier
                            .size(7.dp)
                            .background(dotColor)
                            .cornerRadius(4.dp)
                    ) {}
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = pacing?.statusText ?: "Goal: $percentDisplay%",
                        maxLines = 1,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                }
            }
        }
    }
}

class ProgressMeterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressMeterWidget()
}


