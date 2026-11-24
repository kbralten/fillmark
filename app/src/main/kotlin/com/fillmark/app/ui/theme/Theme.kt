/*
 * Fillmark - Local-first Android hydration tracker
 * Copyright (C) 2026 Fillmark Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.fillmark.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

internal val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = FillmarkNavy900,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = Color.White,
    secondary = secondaryDark,
    onSecondary = FillmarkNavy900,
    tertiary = tertiaryDark,
    background = Color(0xFF091420),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F1E2E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF162A3D),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF243B52),
    outlineVariant = Color(0xFF1B3145)
)

internal val LightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = Color.White,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = FillmarkNavy900,
    secondary = secondaryLight,
    onSecondary = Color.White,
    tertiary = tertiaryLight,
    background = FillmarkBackground,
    onBackground = FillmarkNavyTitle,
    surface = FillmarkSurface,
    onSurface = FillmarkNavyTitle,
    surfaceVariant = FillmarkSurfaceVariant,
    onSurfaceVariant = FillmarkSlateMuted,
    outline = FillmarkCardBorder,
    outlineVariant = Color(0xFFD6E0EA)
)

@Composable
fun FillmarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

