package com.app.drivedata.metrics.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.app.drivedata.metrics.model.MetricRecord

data class MetricRecordWithReadings(
    @Embedded val record: MetricRecordEntity,
    @Relation(parentColumn = "id", entityColumn = "record_id")
    val readings: List<MetricReadingEntity>,
) {
    fun toRecord(): MetricRecord = record.toRecord(readings.mapNotNull { it.toReading() })
}
