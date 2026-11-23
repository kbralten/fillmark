package com.fillmark.app.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.domain.PacingStatus
import com.fillmark.app.ui.theme.DarkColorScheme
import com.fillmark.app.ui.theme.LightColorScheme
import com.fillmark.app.ui.theme.PaceAheadBg
import com.fillmark.app.ui.theme.PaceAheadBgDark
import com.fillmark.app.ui.theme.PaceAheadIcon
import com.fillmark.app.ui.theme.PaceAheadIconDark
import com.fillmark.app.ui.theme.PaceAheadText
import com.fillmark.app.ui.theme.PaceAheadTextDark
import com.fillmark.app.ui.theme.PaceBehindBg
import com.fillmark.app.ui.theme.PaceBehindBgDark
import com.fillmark.app.ui.theme.PaceBehindIcon
import com.fillmark.app.ui.theme.PaceBehindIconDark
import com.fillmark.app.ui.theme.PaceBehindText
import com.fillmark.app.ui.theme.PaceBehindTextDark
import com.fillmark.app.ui.theme.PaceOnTrackBg
import com.fillmark.app.ui.theme.PaceOnTrackBgDark
import com.fillmark.app.ui.theme.PaceOnTrackIcon
import com.fillmark.app.ui.theme.PaceOnTrackIconDark
import com.fillmark.app.ui.theme.PaceOnTrackText
import com.fillmark.app.ui.theme.PaceOnTrackTextDark

fun resolveWidgetIsDark(context: Context, themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
    }
}

@Composable
fun FillmarkWidgetTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        val colorProviders = when (themeMode) {
            ThemeMode.LIGHT -> ColorProviders(light = LightColorScheme, dark = LightColorScheme)
            ThemeMode.DARK -> ColorProviders(light = DarkColorScheme, dark = DarkColorScheme)
            ThemeMode.SYSTEM -> ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
        }
        GlanceTheme(colors = colorProviders, content = content)
    }
}

fun getAdaptiveTileColor(presetColor: Color, isDark: Boolean): Color {
    val (baseR, baseG, baseB) = if (isDark) Triple(0x18, 0x24, 0x32) else Triple(0xEE, 0xF3, 0xF7)
    val weight = if (isDark) 0.24f else 0.16f
    val r = (baseR * (1f - weight) + (presetColor.red * 255f) * weight).toInt().coerceIn(0, 255)
    val g = (baseG * (1f - weight) + (presetColor.green * 255f) * weight).toInt().coerceIn(0, 255)
    val b = (baseB * (1f - weight) + (presetColor.blue * 255f) * weight).toInt().coerceIn(0, 255)
    return Color(r, g, b)
}

fun getAdaptiveTileVolumeColor(presetColor: Color, isDark: Boolean): Color {
    if (isDark) return presetColor
    // Darken bright preset colors slightly on light backgrounds for legible contrast
    return if (presetColor.luminance() > 0.35f) {
        Color(
            red = (presetColor.red * 0.62f).coerceIn(0f, 1f),
            green = (presetColor.green * 0.62f).coerceIn(0f, 1f),
            blue = (presetColor.blue * 0.62f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else {
        presetColor
    }
}

fun getAdaptivePacingColors(
    status: PacingStatus,
    isDark: Boolean
): Triple<Color, Color, Color> {
    return when (status) {
        PacingStatus.BEHIND -> if (isDark) {
            Triple(PaceBehindBgDark, PaceBehindTextDark, PaceBehindIconDark)
        } else {
            Triple(PaceBehindBg, PaceBehindText, PaceBehindIcon)
        }
        PacingStatus.ON_TRACK -> if (isDark) {
            Triple(PaceOnTrackBgDark, PaceOnTrackTextDark, PaceOnTrackIconDark)
        } else {
            Triple(PaceOnTrackBg, PaceOnTrackText, PaceOnTrackIcon)
        }
        PacingStatus.AHEAD -> if (isDark) {
            Triple(PaceAheadBgDark, PaceAheadTextDark, PaceAheadIconDark)
        } else {
            Triple(PaceAheadBg, PaceAheadText, PaceAheadIcon)
        }
    }
}
