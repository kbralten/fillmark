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
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.PresetEntity
import kotlinx.coroutines.flow.first

class QuickLogBarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = FillmarkDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)

        val initialPresets = database.presetDao().getAllPresets()
        val initialPrefs = prefsRepo.preferencesFlow.first()

        provideContent {
            GlanceTheme {
                val presets by remember {
                    database.presetDao().observeAllPresets()
                }.collectAsState(initial = initialPresets)

                val prefs by remember {
                    prefsRepo.preferencesFlow
                }.collectAsState(initial = initialPrefs)

                QuickLogBarContent(presets = presets.take(4), displayUnit = prefs.displayUnit)
            }
        }
    }

    @Composable
    private fun QuickLogBarContent(presets: List<PresetEntity>, displayUnit: com.fillmark.app.core.DisplayUnit) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            if (presets.isEmpty()) {
                Text(
                    text = "Tap to open Fillmark",
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface)
                )
            } else {
                for (preset in presets) {
                    val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, displayUnit)
                    val iconRes = PresetIcon.getDrawableRes(preset.iconKey)
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .background(GlanceTheme.colors.primaryContainer)
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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.onPrimaryContainer
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(1.dp))
                            Text(
                                text = "+$volumeStr",
                                maxLines = 1,
                                style = TextStyle(
                                    fontSize = 10.sp,
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

class QuickLogBarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogBarWidget()
}

