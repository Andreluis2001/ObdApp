package com.app.drivedata.metrics

import com.app.drivedata.metrics.dao.MetricRecordDao
import com.app.drivedata.metrics.dao.MetricRecordEntity
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricsRepositoryTest {

    @Test
    fun save_persistsVinRpmTimestampAndPendingStatus() =
        runBlocking {
            val dao = RecordingMetricRecordDao()
            val repository = MetricsRepository(dao, idGenerator = { "record-1" })

            repository.save(
                snapshot(
                    vin = "93YBSR7RHEJ298116",
                    rpm = ReadingValue.Available(1065.0),
                    collectedAtMillis = 1_700_000_000_000L,
                ),
            )

            val record = dao.inserted.single()
            assertEquals("record-1", record.id)
            assertEquals("93YBSR7RHEJ298116", record.vin)
            assertEquals(1065.0, record.rpm!!, 0.001)
            assertEquals(1_700_000_000_000L, record.collectedAtMillis)
            assertEquals(UploadStatus.PENDING, record.uploadStatus)
        }

    @Test
    fun save_storesNullRpm_whenRpmIsUnavailable() =
        runBlocking {
            val dao = RecordingMetricRecordDao()
            val repository = MetricsRepository(dao, idGenerator = { "record-1" })

            repository.save(
                snapshot(
                    vin = null,
                    rpm = ReadingValue.Unavailable,
                    collectedAtMillis = 1L,
                ),
            )

            val record = dao.inserted.single()
            assertNull(record.vin)
            assertNull("unavailable must never be stored as zero", record.rpm)
        }

    @Test
    fun save_givesEachRecordAStableDistinctId() =
        runBlocking {
            val dao = RecordingMetricRecordDao()
            val repository = MetricsRepository(dao)

            repository.save(snapshot(rpm = ReadingValue.Available(800.0), collectedAtMillis = 1L))
            repository.save(snapshot(rpm = ReadingValue.Available(900.0), collectedAtMillis = 2L))

            assertEquals(2, dao.inserted.size)
            assertNotEquals(dao.inserted[0].id, dao.inserted[1].id)
        }

    private fun snapshot(
        vin: String? = null,
        rpm: ReadingValue,
        collectedAtMillis: Long,
    ): MetricsSnapshot =
        MetricsSnapshot(
            timestampMillis = collectedAtMillis,
            vin = vin,
            readings =
                listOf(
                    MetricReading(id = MetricId.RPM, unit = "RPM", value = rpm),
                ),
        )
}

/** In-memory DAO stand-in; keeps repository mapping tests free of the Android runtime. */
private class RecordingMetricRecordDao : MetricRecordDao {
    val inserted = mutableListOf<MetricRecordEntity>()

    override suspend fun insert(record: MetricRecordEntity) {
        inserted += record
    }

    override suspend fun findByStatus(status: UploadStatus, limit: Int): List<MetricRecordEntity> =
        inserted.filter { it.uploadStatus == status }.take(limit)

    override suspend fun updateStatus(id: String, status: UploadStatus) {
        val index = inserted.indexOfFirst { it.id == id }
        if (index >= 0) {
            inserted[index] = inserted[index].copy(uploadStatus = status)
        }
    }

    override fun observeCountByStatus(status: UploadStatus): Flow<Int> =
        flowOf(inserted.count { it.uploadStatus == status })

    override fun observeLatestCollectedAtMillis(): Flow<Long?> =
        flowOf(inserted.maxOfOrNull { it.collectedAtMillis })
}
