package com.fillmark.app.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fillmark.app.MainActivity
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.ShortcutHelper
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.domain.PacingCalculator
import com.fillmark.app.domain.PacingResult
import com.fillmark.app.ui.dashboard.components.parseHexColor
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
            val currentDateTime by remember {
                WidgetTimingHelper.activeClockFlow()
            }.collectAsState(initial = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES))
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

            val canUndo by remember(prefs.lastWidgetLogRecordId, prefs.lastWidgetLogTimeMillis) {
                WidgetTimingHelper.observeCanUndo(
                    lastRecordId = prefs.lastWidgetLogRecordId,
                    lastLogTimeMillis = prefs.lastWidgetLogTimeMillis
                )
            }.collectAsState(
                initial = WidgetTimingHelper.isUndoAvailable(
                    lastRecordId = prefs.lastWidgetLogRecordId,
                    lastLogTimeMillis = prefs.lastWidgetLogTimeMillis
                )
            )

            val manualLogIntent = remember {
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(ShortcutHelper.EXTRA_OPEN_MANUAL_LOG, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            FillmarkWidgetTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColorEnabled
            ) {
                FullDashboardContent(
                    appNameText = context.getString(R.string.app_name),
                    todayLabel = context.getString(R.string.today),
                    progressCd = context.getString(R.string.widget_progress_cd, percentDisplay),
                    manualLogCd = context.getString(R.string.widget_manual_log_cd),
                    undoLabel = context.getString(R.string.action_undo),
                    emptyPresetsText = context.getString(R.string.widget_open_add_presets),
                    plusVolumeFormat = context.getString(R.string.quick_preset_plus_volume),
                    hasTarget = hasTarget,
                    consumedText = VolumeConverter.formatVolume(effectiveTotalMl, prefs.displayUnit),
                    targetText = VolumeConverter.formatVolume(targetMl, prefs.displayUnit),
                    progressBitmap = progressBitmap,
                    percentDisplay = percentDisplay,
                    pacing = pacing,
                    presets = presets,
                    displayUnit = prefs.displayUnit,
                    canUndo = canUndo,
                    isDark = isDark,
                    manualLogIntent = manualLogIntent
                )
            }
        }
    }

    @Composable
    private fun FullDashboardContent(
        appNameText: String,
        todayLabel: String,
        progressCd: String,
        manualLogCd: String,
        undoLabel: String,
        emptyPresetsText: String,
        plusVolumeFormat: String,
        hasTarget: Boolean,
        consumedText: String,
        targetText: String,
        progressBitmap: android.graphics.Bitmap,
        percentDisplay: Int,
        pacing: PacingResult?,
        presets: List<PresetEntity>,
        displayUnit: DisplayUnit,
        canUndo: Boolean,
        isDark: Boolean,
        manualLogIntent: Intent
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(22.dp)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // TOP SECTION: Header row (separate manual-log button) + progress area
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
                    ),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                if (canUndo) {
                    Box(
                        modifier = GlanceModifier
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clickable(actionRunCallback<UndoWidgetLogActionCallback>())
                    ) {
                        Text(
                            text = undoLabel,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onPrimaryContainer
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }
                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .semantics { contentDescription = manualLogCd }
                        .clickable(actionStartActivity(manualLogIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onPrimaryContainer
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = if (hasTarget) "$percentDisplay%" else todayLabel,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                )
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Spacer(modifier = GlanceModifier.height(5.dp))

                Image(
                    provider = ImageProvider(progressBitmap),
                    contentDescription = progressCd,
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

            Spacer(modifier = GlanceModifier.defaultWeight())

            // BOTTOM SECTION: Presets Row
            if (presets.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyPresetsText,
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    val visiblePresets = presets.take(5)
                    visiblePresets.forEachIndexed { index, preset ->
                        val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, displayUnit)
                        val iconRes = PresetIcon.getDrawableRes(preset.iconKey)
                        val presetColor = parseHexColor(preset.colorHex)
                        val tileBg = getAdaptiveTileColor(presetColor, isDark)
                        val volumeTextColor = getAdaptiveTileVolumeColor(presetColor, isDark)

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
                                    modifier = GlanceModifier.size(16.dp)
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = preset.label,
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlanceTheme.colors.onSurface
                                    )
                                )
                                Text(
                                    text = String.format(plusVolumeFormat, volumeStr),
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(volumeTextColor)
                                    )
                                )
                            }
                        }
                        if (index < visiblePresets.lastIndex) {
                            Spacer(modifier = GlanceModifier.width(4.dp))
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
