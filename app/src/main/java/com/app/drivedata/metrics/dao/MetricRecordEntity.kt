package com.app.drivedata.metrics.dao

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.drivedata.metrics.model.MetricRecord
import com.app.drivedata.metrics.model.UploadStatus

@Entity(
    tableName = "metric_records",
    indices = [Index(value = ["upload_status", "collected_at_millis"])],
)
data class MetricRecordEntity(
    @PrimaryKey val id: String,
    val vin: String?,
    val rpm: Double?,
    @ColumnInfo(name = "collected_at_millis") val collectedAtMillis: Long,
    @ColumnInfo(name = "upload_status") val uploadStatus: UploadStatus,
) {
    fun toRecord(): MetricRecord =
        MetricRecord(
            id = id,
            vin = vin,
            rpm = rpm,
            collectedAtMillis = collectedAtMillis,
            uploadStatus = uploadStatus,
        )
}
