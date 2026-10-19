package com.app.drivedata.metrics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.drivedata.metrics.dao.DriveDataDatabase
import com.app.drivedata.metrics.dao.MetricRecordDao
import com.app.drivedata.metrics.dao.MetricRecordEntity
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetricRecordDaoTest {

    private lateinit var database: DriveDataDatabase
    private lateinit var dao: MetricRecordDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, DriveDataDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.metricRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findByStatus_returnsPendingRecordsInCollectionOrder() =
        runBlocking {
            dao.insert(record(id = "newer", collectedAtMillis = 2_000L))
            dao.insert(record(id = "older", collectedAtMillis = 1_000L))
            dao.insert(record(id = "uploaded", collectedAtMillis = 500L, status = UploadStatus.UPLOADED))

            val pending = dao.findByStatus(UploadStatus.PENDING)

            assertEquals(listOf("older", "newer"), pending.map { it.id })
        }

    @Test
    fun updateStatus_removesRecordFromPendingQueue() =
        runBlocking {
            dao.insert(record(id = "record-1", collectedAtMillis = 1_000L))

            dao.updateStatus("record-1", UploadStatus.UPLOADED)

            assertTrue(dao.findByStatus(UploadStatus.PENDING).isEmpty())
            assertEquals(1, dao.findByStatus(UploadStatus.UPLOADED).size)
        }

    @Test
    fun observeCountByStatus_countsOnlyMatchingRecords() =
        runBlocking {
            dao.insert(record(id = "pending-1", collectedAtMillis = 1_000L))
            dao.insert(record(id = "uploaded", collectedAtMillis = 2_000L, status = UploadStatus.UPLOADED))
            dao.insert(record(id = "pending-2", collectedAtMillis = 3_000L))

            assertEquals(2, dao.observeCountByStatus(UploadStatus.PENDING).first())
            assertEquals(1, dao.observeCountByStatus(UploadStatus.UPLOADED).first())
        }

    @Test
    fun observeLatestCollectedAtMillis_returnsTheNewestTimestamp() =
        runBlocking {
            dao.insert(record(id = "older", collectedAtMillis = 1_000L))
            dao.insert(record(id = "newer", collectedAtMillis = 3_000L))
            dao.insert(record(id = "middle", collectedAtMillis = 2_000L))

            assertEquals(3_000L, dao.observeLatestCollectedAtMillis().first())
        }

    @Test
    fun observeLatestCollectedAtMillis_isNullWithoutRecords() =
        runBlocking {
            assertEquals(null, dao.observeLatestCollectedAtMillis().first())
        }

    private fun record(
        id: String,
        collectedAtMillis: Long,
        status: UploadStatus = UploadStatus.PENDING,
    ): MetricRecordEntity =
        MetricRecordEntity(
            id = id,
            vin = "93YBSR7RHEJ298116",
            rpm = 900.0,
            collectedAtMillis = collectedAtMillis,
            uploadStatus = status,
        )
}
