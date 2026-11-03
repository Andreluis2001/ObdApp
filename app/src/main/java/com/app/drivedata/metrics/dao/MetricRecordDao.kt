package com.app.drivedata.metrics.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricRecordDao {

    @Insert
    suspend fun insertRecord(record: MetricRecordEntity)

    @Insert
    suspend fun insertReadings(readings: List<MetricReadingEntity>)

    @Transaction
    suspend fun insertWithReadings(record: MetricRecordEntity, readings: List<MetricReadingEntity>) {
        insertRecord(record)
        insertReadings(readings)
    }

    @Transaction
    @Query(
        "SELECT * FROM metric_records WHERE owner_username = :owner AND upload_status = :status " +
            "ORDER BY collected_at_millis ASC, id ASC LIMIT :limit",
    )
    suspend fun findWithReadingsByStatus(
        owner: String,
        status: UploadStatus,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): List<MetricRecordWithReadings>

    @Transaction
    @Query("SELECT * FROM metric_records WHERE id = :id LIMIT 1")
    suspend fun findWithReadingsById(id: String): MetricRecordWithReadings?

    @Query(
        "UPDATE metric_records SET upload_status = :status, uploaded_at_millis = :uploadedAtMillis, " +
            "last_error = NULL WHERE id IN (:ids) AND upload_status = :pendingStatus",
    )
    suspend fun markUploaded(
        ids: List<String>,
        status: UploadStatus,
        pendingStatus: UploadStatus,
        uploadedAtMillis: Long,
    )

    @Query(
        "UPDATE metric_records SET upload_status = :rejectedStatus, last_error = :error, " +
            "attempt_count = attempt_count + 1 WHERE id IN (:ids) AND upload_status = :pendingStatus",
    )
    suspend fun markRejected(
        ids: List<String>,
        rejectedStatus: UploadStatus,
        pendingStatus: UploadStatus,
        error: String,
    )

    @Query(
        "UPDATE metric_records SET attempt_count = attempt_count + 1, last_error = :error " +
            "WHERE id IN (:ids) AND upload_status = :pendingStatus",
    )
    suspend fun recordAttemptFailure(
        ids: List<String>,
        pendingStatus: UploadStatus,
        error: String,
    )

    @Query(
        "SELECT COUNT(*) FROM metric_records WHERE owner_username = :owner AND upload_status = :status",
    )
    fun observeCountByStatus(owner: String, status: UploadStatus): Flow<Int>

    @Query(
        "SELECT MAX(uploaded_at_millis) FROM metric_records " +
            "WHERE owner_username = :owner AND upload_status = :status",
    )
    fun observeLatestUploadedAtMillis(owner: String, status: UploadStatus): Flow<Long?>

    @Query(
        "DELETE FROM metric_readings WHERE record_id IN (" +
            "SELECT id FROM metric_records WHERE upload_status = :uploadedStatus " +
            "AND uploaded_at_millis IS NOT NULL AND uploaded_at_millis < :cutoffMillis)",
    )
    suspend fun deleteReadingsForUploadedBefore(cutoffMillis: Long, uploadedStatus: UploadStatus)

    @Query(
        "DELETE FROM metric_records WHERE upload_status = :uploadedStatus " +
            "AND uploaded_at_millis IS NOT NULL AND uploaded_at_millis < :cutoffMillis",
    )
    suspend fun deleteUploadedBefore(cutoffMillis: Long, uploadedStatus: UploadStatus): Int

    @Transaction
    suspend fun pruneUploadedBefore(
        cutoffMillis: Long,
        uploadedStatus: UploadStatus = UploadStatus.UPLOADED,
    ): Int {
        deleteReadingsForUploadedBefore(cutoffMillis, uploadedStatus)
        return deleteUploadedBefore(cutoffMillis, uploadedStatus)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
