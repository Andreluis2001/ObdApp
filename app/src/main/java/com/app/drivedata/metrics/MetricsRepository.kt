package com.app.drivedata.metrics

import com.app.drivedata.metrics.dao.MetricReadingEntity
import com.app.drivedata.metrics.dao.MetricRecordDao
import com.app.drivedata.metrics.dao.MetricRecordEntity
import com.app.drivedata.metrics.dao.StoredRecordSummary
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MetricsRepository(
    private val dao: MetricRecordDao,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Persists one snapshot and all of its readings atomically. Returns the stable record id. */
    suspend fun save(snapshot: MetricsSnapshot, ownerUsername: String): String {
        val id = idGenerator()
        val record =
            MetricRecordEntity(
                id = id,
                ownerUsername = ownerUsername,
                vin = snapshot.vin,
                collectedAtMillis = snapshot.timestampMillis,
                uploadStatus = UploadStatus.PENDING,
            )
        val readings = snapshot.readings.map { MetricReadingEntity.from(id, it) }
        dao.insertWithReadings(record, readings)
        return id
    }

    fun observePendingCount(ownerUsername: String): Flow<Int> =
        dao.observeCountByStatus(ownerUsername, UploadStatus.PENDING)

    fun observeRejectedCount(ownerUsername: String): Flow<Int> =
        dao.observeCountByStatus(ownerUsername, UploadStatus.REJECTED)

    fun observeLastUploadedAtMillis(ownerUsername: String): Flow<Long?> =
        dao.observeLatestUploadedAtMillis(ownerUsername, UploadStatus.UPLOADED)

    fun observeUploadedCount(ownerUsername: String): Flow<Int> =
        dao.observeCountByStatus(ownerUsername, UploadStatus.UPLOADED)

    fun observeTotalCount(ownerUsername: String): Flow<Int> =
        dao.observeTotalCount(ownerUsername)

    fun observeRecentRecords(
        ownerUsername: String,
        limit: Int = MetricRecordDao.DEFAULT_RECENT_LIMIT,
    ): Flow<List<StoredRecordSummary>> = dao.observeRecentRecords(ownerUsername, limit)

    suspend fun pruneUploadedBefore(cutoffMillis: Long = clock() - DEFAULT_RETENTION_MILLIS): Int =
        dao.pruneUploadedBefore(cutoffMillis)

    companion object {
        const val DEFAULT_RETENTION_MILLIS = 3L * 24 * 60 * 60 * 1_000

        /** Estimate used by the dashboard and offline screen for pending storage size. */
        const val ESTIMATED_RECORD_BYTES = 4L * 1024
    }
}
