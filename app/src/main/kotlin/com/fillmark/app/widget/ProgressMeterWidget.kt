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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.domain.PacingCalculator
import com.fillmark.app.domain.PacingResult
import com.fillmark.app.domain.PacingStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

class ProgressMeterWidget : GlanceAppWidget() {
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

            ProgressMeterContent(
                hasTarget = hasTarget,
                consumedText = VolumeConverter.formatVolume(effectiveTotalMl, prefs.displayUnit),
                targetText = VolumeConverter.formatVolume(targetMl, prefs.displayUnit),
                progressBitmap = progressBitmap,
                percentDisplay = percentDisplay,
                pacing = pacing
            )
        }
    }

    @Composable
    private fun ProgressMeterContent(
        hasTarget: Boolean,
        consumedText: String,
        targetText: String,
        progressBitmap: android.graphics.Bitmap,
        percentDisplay: Int,
        pacing: PacingResult?
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xF0182432)))
                .cornerRadius(22.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Top Row: Title + Percentage
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

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Center Luminous Progress Bar
            Image(
                provider = ImageProvider(progressBitmap),
                contentDescription = "Progress: $percentDisplay%",
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxWidth().height(18.dp)
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Bottom Row: Volume consumed / target + Pacing status pill
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
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Box(
                                modifier = GlanceModifier
                                    .size(6.dp)
                                    .background(ColorProvider(dotColor))
                                    .cornerRadius(3.dp)
                            ) {}
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Text(
                                text = pacing.statusText,
                                maxLines = 1,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(textColor)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class ProgressMeterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressMeterWidget()
}
