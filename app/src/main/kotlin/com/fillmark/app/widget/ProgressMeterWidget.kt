package com.fillmark.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import com.fillmark.app.R
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.domain.PacingCalculator
import com.fillmark.app.domain.PacingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
            val refreshTick by WidgetTimingHelper.clockRefreshVersion.collectAsState()
            val clockState by remember(refreshTick) {
                WidgetTimingHelper.activeClockFlow().map { refreshTick to it }
            }.collectAsState(initial = refreshTick to LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES))
            val currentDateTime = if (clockState.first == refreshTick) {
                clockState.second
            } else {
                remember(refreshTick) { LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) }
            }
            val currentDate = currentDateTime.toLocalDate()
            val currentTime = currentDateTime.toLocalTime()

            val startMillis = TimeUtils.startOfDayMillis(currentDate)
            val endMillis = TimeUtils.endOfDayMillis(currentDate)
            val dateIso = currentDate.toString()

            val todayRecords by remember(startMillis, endMillis) {
                database.hydrationDao().observeRecordsBetween(startMillis, endMillis)
            }.collectAsState(initial = if (startMillis == initialStartMillis) initialRecords else emptyList())

            val targetEntity by remember(dateIso) {
                database.dailyTargetDao().observeTargetForDate(dateIso)
            }.collectAsState(initial = if (dateIso == initialDateIso) initialTarget else null)

            val prefs by remember {
                prefsRepo.preferencesFlow
            }.collectAsState(initial = initialPrefs)

            val size = LocalSize.current
            val barWidthDp = if (size.width > 30.dp) {
                (size.width - 28.dp).value.toInt().coerceAtLeast(100)
            } else {
                280
            }

            val isDark = resolveWidgetIsDark(context, prefs.themeMode)
            val effectiveTotalMl = todayRecords.sumOf { it.countedVolumeMl(prefs.hydrationFactorsEnabled) }
            val targetMl = targetEntity?.targetMl ?: prefs.defaultDailyTargetMl
            val hasTarget = targetMl > 0
            val rawRatio = if (hasTarget) (effectiveTotalMl / targetMl).toFloat() else 0f
            val percentDisplay = (rawRatio * 100).roundToInt()

            val pacing = if (hasTarget && prefs.wakingPacingEnabled) {
                val rawPacing = PacingCalculator.evaluatePacing(
                    currentTime = currentTime,
                    wakeTimeString = prefs.wakingHoursStart,
                    sleepTimeString = prefs.wakingHoursEnd,
                    currentVolumeMl = effectiveTotalMl,
                    dailyTargetMl = targetMl,
                    displayUnit = prefs.displayUnit
                )
                rawPacing.copy(
                    statusText = PacingCalculator.formatStatusText(context, rawPacing, prefs.displayUnit)
                )
            } else {
                null
            }

            val paceRatio = if (pacing != null) (pacing.targetPacingPercent / 100f) else null
            val progressBitmap = remember(rawRatio, paceRatio, barWidthDp, isDark) {
                WidgetProgressBarGenerator.createProgressBarBitmap(
                    context = context,
                    progress = rawRatio,
                    paceRatio = paceRatio,
                    widthDp = barWidthDp,
                    heightDp = 18,
                    isDark = isDark
                )
            }

            SideEffect {
                WidgetTimingHelper.markWidgetComposed(id, refreshTick)
            }

            FillmarkWidgetTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColorEnabled
            ) {
                ProgressMeterContent(
                    appNameText = context.getString(R.string.app_name),
                    todayLabel = context.getString(R.string.today),
                    progressCd = context.getString(R.string.widget_progress_cd, percentDisplay),
                    hasTarget = hasTarget,
                    consumedText = VolumeConverter.formatVolume(effectiveTotalMl, prefs.displayUnit),
                    targetText = VolumeConverter.formatVolume(targetMl, prefs.displayUnit),
                    progressBitmap = progressBitmap,
                    percentDisplay = percentDisplay,
                    pacing = pacing,
                    isDark = isDark
                )
            }
        }
    }

    @Composable
    private fun ProgressMeterContent(
        appNameText: String,
        todayLabel: String,
        progressCd: String,
        hasTarget: Boolean,
        consumedText: String,
        targetText: String,
        progressBitmap: android.graphics.Bitmap,
        percentDisplay: Int,
        pacing: PacingResult?,
        isDark: Boolean
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(22.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = appNameText,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (hasTarget) "$percentDisplay%" else todayLabel,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Image(
                provider = ImageProvider(progressBitmap),
                contentDescription = progressCd,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxWidth().height(18.dp)
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = if (hasTarget) "$consumedText / $targetText" else consumedText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                if (hasTarget && pacing != null) {
                    val (pillBg, textColor, dotColor) = getAdaptivePacingColors(pacing.status, isDark)

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
                                    fontSize = 11.sp,
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

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetTimingHelper.notifyClockRefresh()
        MidnightAlarmScheduler.scheduleMidnightReset(context)
        WidgetRefreshScheduler.ensurePeriodicWorkScheduled(context)
        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { WidgetRefreshScheduler.syncSchedule(appContext) }
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightAlarmScheduler.scheduleMidnightReset(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetRefreshScheduler.syncSchedule(context)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetRefreshScheduler.syncSchedule(context)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
