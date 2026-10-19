package com.app.drivedata.metrics

import com.app.drivedata.metrics.dao.MetricRecordDao
import com.app.drivedata.metrics.dao.MetricRecordEntity
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MetricsRepository(
    private val dao: MetricRecordDao,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun save(snapshot: MetricsSnapshot) {
        dao.insert(snapshot.toEntity(idGenerator()))
    }

    /** Number of records waiting to be uploaded, kept live for the dashboard. */
    fun observePendingCount(): Flow<Int> = dao.observeCountByStatus(UploadStatus.PENDING)

    /** Timestamp of the newest recorded snapshot, or null when nothing was collected yet. */
    fun observeLastCollectedAtMillis(): Flow<Long?> = dao.observeLatestCollectedAtMillis()

    private fun MetricsSnapshot.toEntity(id: String): MetricRecordEntity =
        MetricRecordEntity(
            id = id,
            vin = vin,
            rpm = (readings.firstOrNull { it.id == MetricId.RPM }?.value as? ReadingValue.Available)?.value,
            collectedAtMillis = timestampMillis,
            uploadStatus = UploadStatus.PENDING,
        )
}
