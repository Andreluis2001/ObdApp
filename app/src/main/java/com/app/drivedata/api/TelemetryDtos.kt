package com.app.drivedata.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BatchUploadRequest(
    val records: List<TelemetryRecordDto>,
)

@Serializable
data class TelemetryRecordDto(
    val id: String,
    val vin: String,
    @SerialName("collected_at") val collectedAt: String,
    val readings: List<TelemetryReadingDto>,
)

@Serializable
data class TelemetryReadingDto(
    val metric: String,
    val unit: String,
    val available: Boolean,
    val value: Double? = null,
)

@Serializable
data class BatchUploadResponse(
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("received_at") val receivedAt: String = "",
    val accepted: Int,
    val rejected: List<RejectedRecordDto> = emptyList(),
)

@Serializable
data class RejectedRecordDto(
    val index: Int? = null,
    val id: String,
    val vin: String? = null,
    val reason: String = "",
) {
    fun reasonText(): String = reason.ifBlank { "record rejected by the server" }
}
