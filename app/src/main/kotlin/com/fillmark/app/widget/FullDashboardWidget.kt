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
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.fillmark.app.MainActivity
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.domain.PacingCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

class FullDashboardWidget : GlanceAppWidget() {
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
        val initialPresets = database.presetDao().getAllPresets()

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

                val presets by remember {
                    database.presetDao().observeAllPresets()
                }.collectAsState(initial = initialPresets)

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

                FullDashboardContent(
                    consumedText = VolumeConverter.formatVolume(effectiveTotalMl, prefs.displayUnit),
                    targetText = VolumeConverter.formatVolume(targetMl, prefs.displayUnit),
                    progress = progress,
                    percentDisplay = percentDisplay,
                    statusText = pacing?.statusText ?: "Daily goal: $percentDisplay%",
                    presets = presets.take(4),
                    displayUnit = prefs.displayUnit
                )
            }
        }
    }

    @Composable
    private fun FullDashboardContent(
        consumedText: String,
        targetText: String,
        progress: Float,
        percentDisplay: Int,
        statusText: String,
        presets: List<PresetEntity>,
        displayUnit: DisplayUnit
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // TOP SECTION: Progress widget (stacked)
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>())
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

            Spacer(modifier = GlanceModifier.height(8.dp))

            // BOTTOM SECTION: Presets carousel row (stacked below progress bar)
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                if (presets.isEmpty()) {
                    Text(
                        text = "Open Fillmark to add presets",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                } else {
                    for (preset in presets) {
                        val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, displayUnit)
                        val iconRes = PresetIcon.getDrawableRes(preset.iconKey)
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .padding(horizontal = 3.dp)
                                .background(GlanceTheme.colors.primaryContainer)
                                .cornerRadius(10.dp)
                                .clickable(
                                    actionRunCallback<QuickLogActionCallback>(
                                        actionParametersOf(QuickLogActionCallback.PRESET_ID_KEY to preset.id)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                                verticalAlignment = Alignment.Vertical.CenterVertically,
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 2.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(iconRes),
                                    contentDescription = preset.label,
                                    modifier = GlanceModifier.size(18.dp)
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = preset.label,
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlanceTheme.colors.onPrimaryContainer
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = "+$volumeStr",
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 9.sp,
                                        color = GlanceTheme.colors.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class FullDashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FullDashboardWidget()
}

