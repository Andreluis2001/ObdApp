package com.app.drivedata.metrics.dao

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricRecord
import com.app.drivedata.metrics.model.UploadStatus

@Entity(
    tableName = "metric_records",
    indices = [
        Index(
            name = "index_metric_records_owner_status_collected",
            value = ["owner_username", "upload_status", "collected_at_millis"],
        ),
    ],
)
data class MetricRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String?,
    val vin: String?,
    @ColumnInfo(name = "collected_at_millis") val collectedAtMillis: Long,
    @ColumnInfo(name = "upload_status") val uploadStatus: UploadStatus,
    @ColumnInfo(name = "uploaded_at_millis") val uploadedAtMillis: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int = 0,
) {
    fun toRecord(readings: List<MetricReading>): MetricRecord =
        MetricRecord(
            id = id,
            ownerUsername = ownerUsername,
            vin = vin,
            collectedAtMillis = collectedAtMillis,
            readings = readings,
            uploadStatus = uploadStatus,
            uploadedAtMillis = uploadedAtMillis,
            lastError = lastError,
            attemptCount = attemptCount,
        )
}
