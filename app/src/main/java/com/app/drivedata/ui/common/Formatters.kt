package com.app.drivedata.ui.common

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val KILOBYTE = 1024L
private const val MEGABYTE = KILOBYTE * 1024L
private const val GIGABYTE = MEGABYTE * 1024L

/** Renders whole numbers without a decimal part and keeps at most one decimal otherwise. */
fun formatMetricValue(value: Double, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 1
    return format.format(value)
}

fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < KILOBYTE -> "$safe B"
        safe < MEGABYTE -> "${formatUnit(safe / KILOBYTE.toDouble(), locale)} KB"
        safe < GIGABYTE -> "${formatUnit(safe / MEGABYTE.toDouble(), locale)} MB"
        else -> "${formatUnit(safe / GIGABYTE.toDouble(), locale)} GB"
    }
}

fun isSameDay(millis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
}

fun formatClockTime(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("HH:mm", locale).format(Date(millis))

fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("dd/MM, HH:mm", locale).format(Date(millis))

private fun formatUnit(value: Double, locale: Locale): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(locale, "%.1f", value)
    }
