package com.fillmark.app.core

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.fillmark.app.MainActivity
import com.fillmark.app.R
import com.fillmark.app.data.local.FillmarkDatabase
import kotlinx.coroutines.flow.first

object ShortcutHelper {
    const val EXTRA_QUICK_LOG_PRESET_ID = "extra_quick_log_preset_id"
    const val EXTRA_OPEN_MANUAL_LOG = "extra_open_manual_log"

    suspend fun updateShortcuts(context: Context) {
        runCatching {
            val db = FillmarkDatabase.getInstance(context)
            val presets = db.presetDao().getAllPresets()
            val prefs = com.fillmark.app.data.local.UserPreferencesRepository(context).preferencesFlow.first()

            val shortcuts = mutableListOf<ShortcutInfoCompat>()

            presets.take(3).forEach { preset ->
                val volText = VolumeConverter.formatVolume(preset.volumeMl, prefs.displayUnit)
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_QUICK_LOG_PRESET_ID, preset.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                shortcuts.add(
                    ShortcutInfoCompat.Builder(context, "preset_${preset.id}")
                        .setShortLabel("${preset.label} (+$volText)".take(25))
                        .setLongLabel("Log ${preset.label} ($volText)")
                        .setIcon(IconCompat.createWithResource(context, PresetIcon.getDrawableRes(preset.iconKey)))
                        .setIntent(intent)
                        .build()
                )
            }

            val manualIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_OPEN_MANUAL_LOG, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            shortcuts.add(
                ShortcutInfoCompat.Builder(context, "manual_log")
                    .setShortLabel("Custom Drink")
                    .setLongLabel("Log Custom Drink")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_preset_glass))
                    .setIntent(manualIntent)
                    .build()
            )

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
