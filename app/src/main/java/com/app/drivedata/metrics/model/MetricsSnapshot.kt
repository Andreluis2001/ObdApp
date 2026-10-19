package com.app.drivedata.metrics.model


sealed interface ReadingValue {
    data class Available(val value: Double) : ReadingValue

    data object Unavailable : ReadingValue
}

data class MetricReading(
    val id: MetricId,
    val unit: String,
    val value: ReadingValue,
)

data class MetricsSnapshot(
    val timestampMillis: Long,
    val vin: String?,
    val readings: List<MetricReading>,
)
