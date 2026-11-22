package com.app.drivedata.metrics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.drivedata.metrics.dao.DriveDataDatabase
import com.app.drivedata.metrics.dao.MetricReadingEntity
import com.app.drivedata.metrics.dao.MetricRecordDao
import com.app.drivedata.metrics.dao.MetricRecordEntity
import com.app.drivedata.metrics.model.MetricId
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
    fun findWithReadingsByStatus_returnsPendingRecordsInCollectionOrder() =
        runBlocking {
            insertRecord("newer", collectedAtMillis = 2_000L)
            insertRecord("older", collectedAtMillis = 1_000L)
            insertRecord("uploaded", collectedAtMillis = 500L, status = UploadStatus.UPLOADED)

            val pending = dao.findWithReadingsByStatus("admin", UploadStatus.PENDING)

            assertEquals(listOf("older", "newer"), pending.map { it.record.id })
            assertEquals(listOf(MetricId.RPM.name), pending.first().readings.map { it.metricId })
        }

    @Test
    fun findWithReadingsByStatus_onlyReturnsTheOwnersRecords() =
        runBlocking {
            insertRecord("mine", owner = "admin", collectedAtMillis = 1_000L)
            insertRecord("other", owner = "someone-else", collectedAtMillis = 2_000L)

            val pending = dao.findWithReadingsByStatus("admin", UploadStatus.PENDING)

            assertEquals(listOf("mine"), pending.map { it.record.id })
        }

    @Test
    fun markUploaded_removesRecordsFromThePendingQueue() =
        runBlocking {
            insertRecord("record-1", collectedAtMillis = 1_000L)

            dao.markUploaded(
                ids = listOf("record-1"),
                status = UploadStatus.UPLOADED,
                pendingStatus = UploadStatus.PENDING,
                uploadedAtMillis = 5_000L,
            )

            assertTrue(dao.findWithReadingsByStatus("admin", UploadStatus.PENDING).isEmpty())
            val uploaded = dao.findWithReadingsById("record-1")!!.record
            assertEquals(UploadStatus.UPLOADED, uploaded.uploadStatus)
            assertEquals(5_000L, uploaded.uploadedAtMillis)
        }

    @Test
    fun markRejected_keepsTheRecordAndItsReading() =
        runBlocking {
            insertRecord("record-1", collectedAtMillis = 1_000L)

            dao.markRejected(
                ids = listOf("record-1"),
                rejectedStatus = UploadStatus.REJECTED,
                pendingStatus = UploadStatus.PENDING,
                error = "invalid_rpm: rpm must be >= 0",
            )

            val rejected = dao.findWithReadingsById("record-1")!!
            assertEquals(UploadStatus.REJECTED, rejected.record.uploadStatus)
            assertEquals("invalid_rpm: rpm must be >= 0", rejected.record.lastError)
            assertEquals(1, rejected.readings.size)
        }

    @Test
    fun observeCountByStatus_countsOnlyMatchingRecordsForTheOwner() =
        runBlocking {
            insertRecord("pending-1", owner = "admin", collectedAtMillis = 1_000L)
            insertRecord("uploaded", owner = "admin", collectedAtMillis = 2_000L, status = UploadStatus.UPLOADED)
            insertRecord("pending-2", owner = "admin", collectedAtMillis = 3_000L)
            insertRecord("other-pending", owner = "someone-else", collectedAtMillis = 4_000L)

            assertEquals(2, dao.observeCountByStatus("admin", UploadStatus.PENDING).first())
            assertEquals(1, dao.observeCountByStatus("admin", UploadStatus.UPLOADED).first())
            assertEquals(1, dao.observeCountByStatus("someone-else", UploadStatus.PENDING).first())
        }

    @Test
    fun observeLatestUploadedAtMillis_returnsTheNewestUploadTime() =
        runBlocking {
            insertRecord("older", collectedAtMillis = 1_000L, status = UploadStatus.UPLOADED, uploadedAtMillis = 2_000L)
            insertRecord("newer", collectedAtMillis = 2_000L, status = UploadStatus.UPLOADED, uploadedAtMillis = 3_000L)
            insertRecord("pending", collectedAtMillis = 3_000L)

            assertEquals(
                3_000L,
                dao.observeLatestUploadedAtMillis("admin", UploadStatus.UPLOADED).first(),
            )
        }

    @Test
    fun observeRecentRecords_returnsOwnersNewestRecordsWithReadingCount() =
        runBlocking {
            insertRecord("older", collectedAtMillis = 1_000L)
            insertRecord("newer", collectedAtMillis = 2_000L)
            dao.insertReadings(
                listOf(
                    MetricReadingEntity("newer", MetricId.SPEED.name, "km/h", 60.0, true),
                    MetricReadingEntity(
                        "newer",
                        MetricId.COOLANT_TEMPERATURE.name,
                        "°C",
                        87.0,
                        true,
                    ),
                ),
            )
            insertRecord("other", owner = "someone-else", collectedAtMillis = 3_000L)

            val records = dao.observeRecentRecords("admin").first()

            assertEquals(listOf("newer", "older"), records.map { it.id })
            assertEquals(3, records.first().readingCount)
            assertEquals(UploadStatus.PENDING, records.first().uploadStatus)
        }

    @Test
    fun observeRecentRecords_appliesTheLimit() =
        runBlocking {
            insertRecord("older", collectedAtMillis = 1_000L)
            insertRecord("newer", collectedAtMillis = 2_000L)

            val records = dao.observeRecentRecords("admin", limit = 1).first()

            assertEquals(listOf("newer"), records.map { it.id })
        }

    @Test
    fun observeTotalCount_countsOnlyTheOwnersRecords() =
        runBlocking {
            insertRecord("mine-1", collectedAtMillis = 1_000L)
            insertRecord("mine-2", collectedAtMillis = 2_000L, status = UploadStatus.UPLOADED)
            insertRecord("other", owner = "someone-else", collectedAtMillis = 3_000L)

            assertEquals(2, dao.observeTotalCount("admin").first())
        }

    @Test
    fun pruneUploadedBefore_removesOnlyOldUploadedRecordsAndTheirReadings() =
        runBlocking {
            insertRecord("old-uploaded", collectedAtMillis = 1_000L, status = UploadStatus.UPLOADED, uploadedAtMillis = 1_000L)
            insertRecord("new-uploaded", collectedAtMillis = 2_000L, status = UploadStatus.UPLOADED, uploadedAtMillis = 9_000L)
            insertRecord("old-pending", collectedAtMillis = 3_000L)

            val pruned = dao.pruneUploadedBefore(cutoffMillis = 5_000L)

            assertEquals(1, pruned)
            assertEquals(null, dao.findWithReadingsById("old-uploaded"))
            assertEquals(MetricId.RPM.name, dao.findWithReadingsById("new-uploaded")!!.readings.single().metricId)
            assertEquals(1, dao.findWithReadingsByStatus("admin", UploadStatus.PENDING).size)
        }

    private suspend fun insertRecord(
        id: String,
        owner: String = "admin",
        collectedAtMillis: Long,
        status: UploadStatus = UploadStatus.PENDING,
        uploadedAtMillis: Long? = null,
    ) {
        dao.insertWithReadings(
            record =
                MetricRecordEntity(
                    id = id,
                    ownerUsername = owner,
                    vin = "93YBSR7RHEJ298116",
                    collectedAtMillis = collectedAtMillis,
                    uploadStatus = status,
                    uploadedAtMillis = uploadedAtMillis,
                ),
            readings =
                listOf(
                    MetricReadingEntity(
                        recordId = id,
                        metricId = MetricId.RPM.name,
                        unit = "RPM",
                        value = 900.0,
                        isAvailable = true,
                    ),
                ),
        )
    }
}
