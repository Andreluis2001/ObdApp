package com.app.drivedata.metrics.model

enum class UploadStatus {
    PENDING,
    UPLOADED,
    REJECTED,
}

data class MetricRecord(
    val id: String,
    val ownerUsername: String?,
    val vin: String?,
    val collectedAtMillis: Long,
    val readings: List<MetricReading>,
    val uploadStatus: UploadStatus,
    val uploadedAtMillis: Long? = null,
    val lastError: String? = null,
    val attemptCount: Int = 0,
)
