package com.app.drivedata.metrics.dao

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.ReadingValue

@Entity(
    tableName = "metric_readings",
    primaryKeys = ["record_id", "metric_id"],
    foreignKeys = [
        ForeignKey(
            entity = MetricRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "index_metric_readings_record_id", value = ["record_id"])],
)
data class MetricReadingEntity(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "metric_id") val metricId: String,
    val unit: String,
    val value: Double?,
    @ColumnInfo(name = "is_available") val isAvailable: Boolean,
) {
    fun toReading(): MetricReading? {
        val id = MetricId.entries.firstOrNull { it.name == metricId } ?: return null
        return MetricReading(
            id = id,
            unit = unit,
            value = if (isAvailable && value != null) {
                ReadingValue.Available(value)
            } else {
                ReadingValue.Unavailable
            },
        )
    }

    companion object {
        fun from(recordId: String, reading: MetricReading): MetricReadingEntity =
            MetricReadingEntity(
                recordId = recordId,
                metricId = reading.id.name,
                unit = reading.unit,
                value = (reading.value as? ReadingValue.Available)?.value,
                isAvailable = reading.value is ReadingValue.Available,
            )
    }
}
