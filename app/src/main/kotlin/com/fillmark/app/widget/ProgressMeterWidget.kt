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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
                val progress = if (targetMl > 0) (effectiveTotalMl / targetMl).toFloat().coerceIn(0f, 1f) else 0f
                val percentDisplay = (progress * 100).roundToInt()

                val pacing = if (prefs.wakingPacingEnabled) {
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

                ProgressMeterContent(
                    consumedText = VolumeConverter.formatVolume(effectiveTotalMl, prefs.displayUnit),
                    targetText = VolumeConverter.formatVolume(targetMl, prefs.displayUnit),
                    progress = progress,
                    percentDisplay = percentDisplay,
                    statusText = pacing?.statusText ?: "Daily goal: $percentDisplay%"
                )
            }
        }
    }

    @Composable
    private fun ProgressMeterContent(
        consumedText: String,
        targetText: String,
        progress: Float,
        percentDisplay: Int,
        statusText: String
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
                        color = GlanceTheme.colors.onSurface
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp)
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
                Text(
                    text = statusText,
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.primary
                    )
                )
            }
        }
    }
}

class ProgressMeterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressMeterWidget()
}

