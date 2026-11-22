package com.app.drivedata.metrics

import com.app.drivedata.metrics.dao.MetricReadingEntity
import com.app.drivedata.metrics.dao.MetricRecordDao
import com.app.drivedata.metrics.dao.MetricRecordEntity
import com.app.drivedata.metrics.dao.MetricRecordWithReadings
import com.app.drivedata.metrics.dao.StoredRecordSummary
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory DAO stand-in for JVM tests. Mirrors the queue semantics of the Room implementation. */
class FakeMetricRecordDao : MetricRecordDao {
    val records = mutableListOf<MetricRecordEntity>()
    private val readings = mutableMapOf<String, MutableList<MetricReadingEntity>>()

    fun readingsFor(recordId: String): List<MetricReadingEntity> = readings[recordId].orEmpty()

    fun add(
        id: String,
        owner: String = "admin",
        collectedAtMillis: Long,
        status: UploadStatus = UploadStatus.PENDING,
        uploadedAtMillis: Long? = null,
    ) {
        records +=
            MetricRecordEntity(
                id = id,
                ownerUsername = owner,
                vin = "93YBSR7RHEJ298116",
                collectedAtMillis = collectedAtMillis,
                uploadStatus = status,
                uploadedAtMillis = uploadedAtMillis,
            )
        readings[id] =
            mutableListOf(
                MetricReadingEntity(
                    recordId = id,
                    metricId = MetricId.RPM.name,
                    unit = "RPM",
                    value = 900.0,
                    isAvailable = true,
                ),
            )
    }

    override suspend fun insertRecord(record: MetricRecordEntity) {
        records += record
    }

    override suspend fun insertReadings(readings: List<MetricReadingEntity>) {
        readings.forEach { reading ->
            this.readings.getOrPut(reading.recordId) { mutableListOf() } += reading
        }
    }

    override suspend fun findWithReadingsByStatus(
        owner: String,
        status: UploadStatus,
        limit: Int,
    ): List<MetricRecordWithReadings> =
        records
            .filter { it.ownerUsername == owner && it.uploadStatus == status }
            .sortedWith(compareBy({ it.collectedAtMillis }, { it.id }))
            .take(limit)
            .map { it.withReadings() }

    override suspend fun findWithReadingsById(id: String): MetricRecordWithReadings? =
        records.firstOrNull { it.id == id }?.withReadings()

    override suspend fun markUploaded(
        ids: List<String>,
        status: UploadStatus,
        pendingStatus: UploadStatus,
        uploadedAtMillis: Long,
    ) {
        records.replaceAll { record ->
            if (record.id in ids && record.uploadStatus == pendingStatus) {
                record.copy(uploadStatus = status, uploadedAtMillis = uploadedAtMillis, lastError = null)
            } else {
                record
            }
        }
    }

    override suspend fun markRejected(
        ids: List<String>,
        rejectedStatus: UploadStatus,
        pendingStatus: UploadStatus,
        error: String,
    ) {
        records.replaceAll { record ->
            if (record.id in ids && record.uploadStatus == pendingStatus) {
                record.copy(
                    uploadStatus = rejectedStatus,
                    lastError = error,
                    attemptCount = record.attemptCount + 1,
                )
            } else {
                record
            }
        }
    }

    override suspend fun recordAttemptFailure(
        ids: List<String>,
        pendingStatus: UploadStatus,
        error: String,
    ) {
        records.replaceAll { record ->
            if (record.id in ids && record.uploadStatus == pendingStatus) {
                record.copy(attemptCount = record.attemptCount + 1, lastError = error)
            } else {
                record
            }
        }
    }

    override fun observeCountByStatus(owner: String, status: UploadStatus): Flow<Int> =
        flowOf(records.count { it.ownerUsername == owner && it.uploadStatus == status })

    override fun observeLatestUploadedAtMillis(
        owner: String,
        status: UploadStatus,
    ): Flow<Long?> =
        flowOf(
            records
                .filter { it.ownerUsername == owner && it.uploadStatus == status }
                .mapNotNull { it.uploadedAtMillis }
                .maxOrNull(),
        )

    override fun observeRecentRecords(
        owner: String,
        limit: Int,
    ): Flow<List<StoredRecordSummary>> =
        flowOf(
            records
                .filter { it.ownerUsername == owner }
                .sortedWith(
                    compareByDescending<MetricRecordEntity> { it.collectedAtMillis }
                        .thenByDescending { it.id },
                )
                .take(limit)
                .map { record ->
                    StoredRecordSummary(
                        id = record.id,
                        vin = record.vin,
                        collectedAtMillis = record.collectedAtMillis,
                        uploadStatus = record.uploadStatus,
                        uploadedAtMillis = record.uploadedAtMillis,
                        lastError = record.lastError,
                        readingCount = readingsFor(record.id).size,
                    )
                },
        )

    override fun observeTotalCount(owner: String): Flow<Int> =
        flowOf(records.count { it.ownerUsername == owner })

    override suspend fun deleteReadingsForUploadedBefore(
        cutoffMillis: Long,
        uploadedStatus: UploadStatus,
    ) {
        val ids =
            records
                .filter {
                    it.uploadStatus == uploadedStatus &&
                        it.uploadedAtMillis != null &&
                        it.uploadedAtMillis < cutoffMillis
                }
                .map { it.id }
        ids.forEach(readings::remove)
    }

    override suspend fun deleteUploadedBefore(cutoffMillis: Long, uploadedStatus: UploadStatus): Int {
        val before = records.size
        records.removeAll { record ->
            record.uploadStatus == uploadedStatus &&
                record.uploadedAtMillis != null &&
                record.uploadedAtMillis < cutoffMillis
        }
        return before - records.size
    }

    private fun MetricRecordEntity.withReadings(): MetricRecordWithReadings =
        MetricRecordWithReadings(record = this, readings = readingsFor(id))
}
