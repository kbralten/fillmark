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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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

            QuickLogBarContent(
                presets = presets,
                displayUnit = prefs.displayUnit
            )
        }
    }

    @Composable
    private fun QuickLogBarContent(
        presets: List<PresetEntity>,
        displayUnit: DisplayUnit
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xF0182432)))
                .cornerRadius(22.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            if (presets.isEmpty()) {
                Text(
                    text = "Tap to open Fillmark",
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.White))
                )
            } else {
                val visiblePresets = presets.take(5)
                for (preset in visiblePresets) {
                    val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, displayUnit)
                    val iconRes = PresetIcon.getDrawableRes(preset.iconKey)
                    val presetColor = parseHexColor(preset.colorHex)

                    // Tinted squircle preset tile
                    val tileBg = getBlendedTileColor(presetColor)

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
                                modifier = GlanceModifier.size(20.dp)
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
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
                    Spacer(modifier = GlanceModifier.width(5.dp))
                }
            }
        }
    }
}

fun getBlendedTileColor(presetColor: Color): Color {
    val r = (0x18 * (1f - 0.24f) + (presetColor.red * 255f) * 0.24f).toInt().coerceIn(0, 255)
    val g = (0x24 * (1f - 0.24f) + (presetColor.green * 255f) * 0.24f).toInt().coerceIn(0, 255)
    val b = (0x32 * (1f - 0.24f) + (presetColor.blue * 255f) * 0.24f).toInt().coerceIn(0, 255)
    return Color(r, g, b)
}

class QuickLogBarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogBarWidget()
}
