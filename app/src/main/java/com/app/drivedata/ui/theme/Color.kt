package com.app.drivedata.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Brand palette used across the app, matching the dashboard mockup. */

// Dark navy used for the login background overlay. Headers use the lighter gradient below.
val LoginBackgroundTop = Color(0xFF0B1B33)
val LoginBackgroundBottom = Color(0xFF050D1A)
val LoginAccent = Color(0xFF1F7BFF)
val LoginField = Color(0xFF132A4A)
val LoginFieldBorder = Color(0xFF2A3F5F)
val LoginTextMuted = Color(0xFF9AA9BF)
val LoginErrorText = Color(0xFFFF6B6B)

// Dark navy headers: vertical gradient with light status-bar icons.
val HeaderTop = Color(0xFF143D70)
val HeaderBottom = Color(0xFF0D2C52)
val HeaderTextMuted = Color(0xFFC3D0E0)
val HeaderGradient = Brush.verticalGradient(listOf(HeaderTop, HeaderBottom))

// Light content surfaces.
val HomeBackground = Color(0xFFEEF3FA)
val HomeSurface = Color(0xFFFFFFFF)
val HomeTextPrimary = Color(0xFF0B1B33)
val HomeTextSecondary = Color(0xFF5D6C82)
val HomeDivider = Color(0xFFE1E8F2)
val HomeGreen = Color(0xFF1FA855)
val HomeBlue = Color(0xFF1F6FFF)
val HomeOrange = Color(0xFFF29A1A)
val HomePurple = Color(0xFF8B5CF6)
val HomeRed = Color(0xFFE5483C)

// Metric-specific accents. Each metric keeps its own hue for badge and sparkline.
val MetricRpm = Color(0xFF12B886)
val MetricSpeed = HomeBlue
val MetricTemperature = HomeOrange
val MetricLoad = HomePurple
