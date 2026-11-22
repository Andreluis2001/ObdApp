package com.app.drivedata.metrics.dao

import androidx.room.ColumnInfo
import com.app.drivedata.metrics.model.UploadStatus

/** Lightweight projection used by the offline screen to list stored records without loading every reading. */
data class StoredRecordSummary(
    val id: String,
    val vin: String?,
    @ColumnInfo(name = "collected_at_millis") val collectedAtMillis: Long,
    @ColumnInfo(name = "upload_status") val uploadStatus: UploadStatus,
    @ColumnInfo(name = "uploaded_at_millis") val uploadedAtMillis: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "reading_count") val readingCount: Int,
)
