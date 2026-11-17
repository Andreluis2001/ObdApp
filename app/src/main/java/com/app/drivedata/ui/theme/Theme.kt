package com.app.drivedata.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app uses the light dashboard palette only, so the same scheme is applied
 * regardless of the device dark-mode setting. Every slot is pinned to a brand
 * color so components that rely on Material defaults never pick up colors from
 * another palette.
 */
private val DriveDataColorScheme = lightColorScheme(
    primary = HomeBlue,
    onPrimary = Color.White,
    primaryContainer = HomeBlue,
    onPrimaryContainer = Color.White,
    secondary = HomeBlue,
    onSecondary = Color.White,
    tertiary = HomeBlue,
    onTertiary = Color.White,
    background = HomeBackground,
    onBackground = HomeTextPrimary,
    surface = HomeSurface,
    onSurface = HomeTextPrimary,
    surfaceVariant = HomeBackground,
    onSurfaceVariant = HomeTextSecondary,
    surfaceContainerLowest = HomeSurface,
    surfaceContainerLow = HomeSurface,
    surfaceContainer = HomeSurface,
    surfaceContainerHigh = HomeSurface,
    surfaceContainerHighest = HomeSurface,
    surfaceTint = HomeBlue,
    outline = HomeTextSecondary,
    outlineVariant = HomeDivider,
    error = HomeRed,
    onError = Color.White,
    errorContainer = HomeRed,
    onErrorContainer = Color.White,
    inverseSurface = LoginBackgroundTop,
    inverseOnSurface = Color.White,
    inversePrimary = LoginAccent,
)

@Composable
fun DriveDataTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DriveDataColorScheme,
        typography = Typography,
        content = content,
    )
}
