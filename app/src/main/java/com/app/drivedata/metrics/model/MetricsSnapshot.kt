package com.app.drivedata.metrics.model

/**
 * Outcome of reading a single parameter.
 *
 * [Unavailable] is kept explicit so `NO DATA`, unsupported parameters, and timeouts are never
 * rendered as a numeric zero.
 */
sealed interface ReadingValue {
    data class Available(val value: Double) : ReadingValue

    data object Unavailable : ReadingValue
}

/** One parameter reading within a [MetricsSnapshot]. */
data class MetricReading(
    val id: MetricId,
    val unit: String,
    val value: ReadingValue,
)

/**
 * The result of one full sequential sweep over the metric catalog.
 *
 * [vin] is best-effort (Mode 09 is not supported by every vehicle) and null when unavailable.
 */
data class MetricsSnapshot(
    val timestampMillis: Long,
    val vin: String?,
    val readings: List<MetricReading>,
)
