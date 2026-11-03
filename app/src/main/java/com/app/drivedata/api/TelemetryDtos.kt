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
    val vin: String? = null,
    @SerialName("collected_at") val collectedAt: String,
    val user: String? = null,
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
    val accepted: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val rejected: List<RejectedRecordDto> = emptyList(),
) {
    val acknowledgedIds: List<String>
        get() = accepted + duplicates
}

@Serializable
data class RejectedRecordDto(
    val id: String,
    val code: String? = null,
    val message: String? = null,
) {
    fun reason(): String =
        listOfNotNull(code, message).joinToString(": ").ifBlank { "record rejected by the server" }
}
