package com.app.drivedata.metrics.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricRecordDao {

    @Insert
    suspend fun insert(record: MetricRecordEntity)

    @Query(
        "SELECT * FROM metric_records WHERE upload_status = :status " +
            "ORDER BY collected_at_millis ASC LIMIT :limit",
    )
    suspend fun findByStatus(status: UploadStatus, limit: Int = DEFAULT_PAGE_SIZE): List<MetricRecordEntity>

    @Query("UPDATE metric_records SET upload_status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: UploadStatus)

    @Query("SELECT COUNT(*) FROM metric_records WHERE upload_status = :status")
    fun observeCountByStatus(status: UploadStatus): Flow<Int>

    @Query("SELECT MAX(collected_at_millis) FROM metric_records")
    fun observeLatestCollectedAtMillis(): Flow<Long?>

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
