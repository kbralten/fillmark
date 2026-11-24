package com.fillmark.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.ui.dashboard.components.parseHexColor
import kotlinx.coroutines.flow.first

class QuickLogBarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = FillmarkDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)

        val initialPresets = database.presetDao().getAllPresets()
        val initialPrefs = prefsRepo.preferencesFlow.first()

        provideContent {
            val presets by remember {
                database.presetDao().observeAllPresets()
            }.collectAsState(initial = initialPresets)

            val prefs by remember {
                prefsRepo.preferencesFlow
            }.collectAsState(initial = initialPrefs)

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

            val isDark = resolveWidgetIsDark(context, prefs.themeMode)

            FillmarkWidgetTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColorEnabled
            ) {
                QuickLogBarContent(
                    presets = presets,
                    displayUnit = prefs.displayUnit,
                    canUndo = canUndo,
                    isDark = isDark,
                    emptyText = context.getString(R.string.widget_tap_open),
                    undoLabel = context.getString(R.string.action_undo),
                    plusVolumeFormat = context.getString(R.string.quick_preset_plus_volume)
                )
            }
        }
    }

    @Composable
    private fun QuickLogBarContent(
        presets: List<PresetEntity>,
        displayUnit: DisplayUnit,
        canUndo: Boolean,
        isDark: Boolean,
        emptyText: String,
        undoLabel: String,
        plusVolumeFormat: String
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(22.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            if (presets.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText,
                        style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface)
                    )
                }
            } else {
                val visiblePresets = presets.take(if (canUndo) 4 else 5)
                visiblePresets.forEachIndexed { index, preset ->
                    val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, displayUnit)
                    val iconRes = PresetIcon.getDrawableRes(preset.iconKey)
                    val presetColor = parseHexColor(preset.colorHex)
                    val tileBg = getAdaptiveTileColor(presetColor, isDark)
                    val volumeTextColor = getAdaptiveTileVolumeColor(presetColor, isDark)

                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .height(56.dp)
                            .background(ColorProvider(tileBg))
                            .cornerRadius(14.dp)
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
                            modifier = GlanceModifier.padding(vertical = 4.dp, horizontal = 2.dp)
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
                    if (index < visiblePresets.lastIndex || canUndo) {
                        Spacer(modifier = GlanceModifier.width(5.dp))
                    }
                }
                if (canUndo) {
                    Box(
                        modifier = GlanceModifier
                            .height(56.dp)
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(14.dp)
                            .padding(horizontal = 10.dp)
                            .clickable(actionRunCallback<UndoWidgetLogActionCallback>()),
                        contentAlignment = Alignment.Center
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
                }
            }
        }
    }
}

fun getBlendedTileColor(presetColor: Color): Color {
    return getAdaptiveTileColor(presetColor, isDark = true)
}

class QuickLogBarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogBarWidget()
}
