package com.app.drivedata.metrics.model

enum class UploadStatus {
    PENDING,
    UPLOADED,
}

data class MetricRecord(
    val id: String,
    val vin: String?,
    val rpm: Double?,
    val collectedAtMillis: Long,
    val uploadStatus: UploadStatus,
)
