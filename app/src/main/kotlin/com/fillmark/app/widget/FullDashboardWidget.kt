package com.fillmark.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import androidx.glance.unit.ColorProvider
import com.fillmark.app.MainActivity
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.domain.PacingCalculator
import com.fillmark.app.domain.PacingResult
import com.fillmark.app.domain.PacingStatus
import com.fillmark.app.ui.dashboard.components.parseHexColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

class FullDashboardWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = FillmarkDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)

        val today = LocalDate.now()
        val initialStartMillis = TimeUtils.startOfDayMillis(today)
        val initialEndMillis = TimeUtils.endOfDayMillis(today)
        val initialDateIso = TimeUtils.currentDateIso()

        val initialRecords = database.hydrationDao().getRecordsBetween(initialStartMillis, initialEndMillis)
        val initialTarget = database.dailyTargetDao().getTargetForDate(initialDateIso)
        val initialPrefs = prefsRepo.preferencesFlow.first()
        val initialPresets = database.presetDao().getAllPresets()

        provideContent {
            val currentDate by produceState(initialValue = LocalDate.now()) {
                while (true) {
                    val now = LocalDate.now()
                    if (value != now) {
                        value = now
                    }
                    val millisUntilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
                    delay(millisUntilNextMinute.coerceAtLeast(1_000L))
                }
            }

            val startMillis = TimeUtils.startOfDayMillis(currentDate)
            val endMillis = TimeUtils.endOfDayMillis(currentDate)
            val dateIso = currentDate.toString()

            val todayRecords by remember(startMillis, endMillis) {
                database.hydrationDao().observeRecordsBetween(startMillis, endMillis)
            }.collectAsState(initial = if (startMillis == initialStartMillis) initialRecords else emptyList())

            val targetEntity by remember(dateIso) {
                database.dailyTargetDao().observeTargetForDate(dateIso)
            }.collectAsState(initial = initialTarget)

            val prefs by remember {
                prefsRepo.preferencesFlow
            }.collectAsState(initial = initialPrefs)

            val presets by remember {
                database.presetDao().observeAllPresets()
            }.collectAsState(initial = initialPresets)

            val size = LocalSize.current
            val barWidthDp = if (size.width > 30.dp) {
                (size.width - 28.dp).value.toInt().coerceAtLeast(100)
            } else {
                280
            }

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
            val progressBitmap = remember(rawRatio, paceRatio, barWidthDp) {
                WidgetProgressBarGenerator.createProgressBarBitmap(
                    context = context,
                    progress = rawRatio,
                    paceRatio = paceRatio,
                    widthDp = barWidthDp,
                    heightDp = 18
                )
            }

            FullDashboardContent(
                hasTarget = hasTarget,
                consumedText = VolumeConverter.formatVolume(effectiveTotalMl, prefs.displayUnit),
                targetText = VolumeConverter.formatVolume(targetMl, prefs.displayUnit),
                progressBitmap = progressBitmap,
                percentDisplay = percentDisplay,
                pacing = pacing,
                presets = presets,
                displayUnit = prefs.displayUnit
            )
        }
    }

    @Composable
    private fun FullDashboardContent(
        hasTarget: Boolean,
        consumedText: String,
        targetText: String,
        progressBitmap: android.graphics.Bitmap,
        percentDisplay: Int,
        pacing: PacingResult?,
        presets: List<PresetEntity>,
        displayUnit: DisplayUnit
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xF0182432)))
                .cornerRadius(22.dp)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // TOP SECTION: Progress area
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color.White)
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = if (hasTarget) "$percentDisplay%" else "Today",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color.White)
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(5.dp))

                Image(
                    provider = ImageProvider(progressBitmap),
                    contentDescription = "Progress: $percentDisplay%",
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(18.dp)
                )

                Spacer(modifier = GlanceModifier.height(5.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = if (hasTarget) "$consumedText / $targetText" else consumedText,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorProvider(Color(0xFF9EAFBF))
                        )
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    if (hasTarget && pacing != null) {
                        val (pillBg, textColor, dotColor) = when (pacing.status) {
                            PacingStatus.BEHIND -> Triple(
                                Color(0xFF3F1F1E),
                                Color(0xFFFF9B8A),
                                Color(0xFFFF7A60)
                            )
                            PacingStatus.ON_TRACK -> Triple(
                                Color(0xFF143B38),
                                Color(0xFF7DE6C0),
                                Color(0xFF2DD4BF)
                            )
                            PacingStatus.AHEAD -> Triple(
                                Color(0xFF173D26),
                                Color(0xFF86EFAC),
                                Color(0xFF4ADE80)
                            )
                        }

                        Box(
                            modifier = GlanceModifier
                                .background(ColorProvider(pillBg))
                                .cornerRadius(12.dp)
                                .padding(horizontal = 7.dp, vertical = 2.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                                Box(
                                    modifier = GlanceModifier
                                        .size(6.dp)
                                        .background(ColorProvider(dotColor))
                                        .cornerRadius(3.dp)
                                ) {}
                                Spacer(modifier = GlanceModifier.width(3.5.dp))
                                Text(
                                    text = pacing.statusText,
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(textColor)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // BOTTOM SECTION: Presets Row
            if (presets.isEmpty()) {
                Text(
                    text = "Open Fillmark to add presets",
                    style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color(0xFF9EAFBF)))
                )
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    val visiblePresets = presets.take(5)
                    for (preset in visiblePresets) {
                        val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, displayUnit)
                        val iconRes = PresetIcon.getDrawableRes(preset.iconKey)
                        val presetColor = parseHexColor(preset.colorHex)
                        val tileBg = getBlendedTileColor(presetColor)

                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(54.dp)
                                .background(ColorProvider(tileBg))
                                .cornerRadius(12.dp)
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
                                modifier = GlanceModifier.padding(vertical = 3.dp, horizontal = 2.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(iconRes),
                                    contentDescription = preset.label,
                                    modifier = GlanceModifier.size(18.dp)
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = preset.label,
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(Color.White)
                                    )
                                )
                                Text(
                                    text = "+$volumeStr",
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(presetColor)
                                    )
                                )
                            }
                        }
                        Spacer(modifier = GlanceModifier.width(4.dp))
                    }
                }
            }
        }
    }
}

class FullDashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FullDashboardWidget()
}
