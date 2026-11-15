package com.app.drivedata.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = HomeBlue,
    secondary = HomeBlue,
    tertiary = HomeBlue,
    background = LoginBackgroundTop,
    surface = HomeSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = HomeBlue,
    secondary = HomeBlue,
    tertiary = HomeBlue,
    background = HomeBackground,
    surface = HomeSurface,
)

@Composable
fun DriveDataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // The brand palette is applied through explicit colors in each screen, so the
    // Material scheme is kept aligned with it (and Material You is never applied).
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}