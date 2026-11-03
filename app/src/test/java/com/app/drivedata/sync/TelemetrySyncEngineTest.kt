package com.app.drivedata.sync

import com.app.drivedata.api.BatchUploadRequest
import com.app.drivedata.api.BatchUploadResponse
import com.app.drivedata.api.RejectedRecordDto
import com.app.drivedata.api.TelemetryApi
import com.app.drivedata.metrics.FakeMetricRecordDao
import com.app.drivedata.metrics.MetricsRepository
import com.app.drivedata.metrics.dao.MetricReadingEntity
import com.app.drivedata.metrics.dao.MetricRecordEntity
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class TelemetrySyncEngineTest {

    @Test
    fun sync_uploadsEveryReadingAndMarksTheRecordUploaded() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { BatchUploadResponse(accepted = listOf("record-1")) }
            val engine = TelemetrySyncEngine(dao, api, clock = { 10_000L })

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(1, (result as SyncRunResult.Success).uploaded)
            assertEquals(UploadStatus.UPLOADED, dao.records.single().uploadStatus)
            assertEquals(10_000L, dao.records.single().uploadedAtMillis)

            val request = api.requests.single()
            val record = request.records.single()
            assertEquals("record-1", record.id)
            assertEquals("admin", record.user)
            assertEquals("1970-01-01T00:00:01.000Z", record.collectedAt)
            assertEquals("93YBSR7RHEJ298116", record.vin)
            assertEquals("rpm", record.readings.single().metric)
            assertEquals(900.0, record.readings.single().value!!, 0.001)
            assertTrue(record.readings.single().available)
        }

    @Test
    fun sync_sendsUnavailableMetricsExplicitly() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.insertRecord(
                MetricRecordEntity(
                    id = "record-1",
                    ownerUsername = "admin",
                    vin = "VIN",
                    collectedAtMillis = 1_000L,
                    uploadStatus = UploadStatus.PENDING,
                ),
            )
            dao.insertReadings(
                listOf(
                    MetricReadingEntity("record-1", MetricId.RPM.name, "RPM", 900.0, true),
                    MetricReadingEntity("record-1", MetricId.FUEL_LEVEL.name, "%", null, false),
                ),
            )
            val api = FakeTelemetryApi { BatchUploadResponse(accepted = listOf("record-1")) }
            val engine = TelemetrySyncEngine(dao, api)

            engine.sync("admin")

            val fuel = api.requests.single().records.single().readings.first { it.metric == "fuel_level" }
            assertEquals(false, fuel.available)
            assertNull(fuel.value)
        }

    @Test
    fun sync_treatsDuplicatesAsAcknowledged() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { BatchUploadResponse(duplicates = listOf("record-1")) }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(UploadStatus.UPLOADED, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_marksRejectedRecordsAndRetainsThem() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi {
                BatchUploadResponse(
                    rejected = listOf(RejectedRecordDto("record-1", "invalid_rpm", "rpm must be >= 0")),
                )
            }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(1, (result as SyncRunResult.Success).rejected)
            val record = dao.records.single()
            assertEquals(UploadStatus.REJECTED, record.uploadStatus)
            assertEquals("invalid_rpm: rpm must be >= 0", record.lastError)
            assertEquals(1, record.attemptCount)
        }

    @Test
    fun sync_keepsRecordsPendingOnANetworkFailure() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { throw IOException("timeout") }
            val engine = TelemetrySyncEngine(dao, api)

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Retryable)
            val record = dao.records.single()
            assertEquals(UploadStatus.PENDING, record.uploadStatus)
            assertEquals(1, record.attemptCount)
            assertEquals(SyncError.NETWORK, engine.status.value.lastError)
        }

    @Test
    fun sync_keepsRecordsPendingAndStopsOnUnauthorized() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { throw httpError(401) }
            val engine = TelemetrySyncEngine(dao, api)

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Failed)
            assertEquals(SyncError.AUTH, (result as SyncRunResult.Failed).error)
            assertEquals(UploadStatus.PENDING, dao.records.single().uploadStatus)
            assertEquals(1, api.requests.size)
        }

    @Test
    fun sync_pagesThroughTheQueueInBatches() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            repeat(150) { index -> dao.add("record-$index", collectedAtMillis = index.toLong()) }
            val api = FakeTelemetryApi { request ->
                BatchUploadResponse(accepted = request.records.map { it.id })
            }
            val engine = TelemetrySyncEngine(dao, api)

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(150, (result as SyncRunResult.Success).uploaded)
            assertEquals(2, api.requests.size)
            assertEquals(100, api.requests[0].records.size)
            assertEquals(50, api.requests[1].records.size)
        }

    @Test
    fun sync_onlyUploadsTheSignedInOwnersRecords() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("mine", owner = "admin", collectedAtMillis = 1_000L)
            dao.add("other", owner = "someone-else", collectedAtMillis = 2_000L)
            val api = FakeTelemetryApi { request ->
                BatchUploadResponse(accepted = request.records.map { it.id })
            }

            TelemetrySyncEngine(dao, api).sync("admin")

            assertEquals(listOf("mine"), api.requests.single().records.map { it.id })
            assertEquals(UploadStatus.PENDING, dao.records.first { it.id == "other" }.uploadStatus)
        }

    @Test
    fun sync_doesNotLoopWhenTheServerReturnsNoAcknowledgement() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { BatchUploadResponse() }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

            assertTrue(result is SyncRunResult.Retryable)
            assertEquals(1, api.requests.size)
            assertEquals(UploadStatus.PENDING, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_prunesUploadedRecordsPastTheThreeDayRetention() =
        runBlocking {
            val now = 10L * 24 * 60 * 60 * 1_000
            val dao = FakeMetricRecordDao()
            dao.add(
                "old-uploaded",
                collectedAtMillis = 1L,
                status = UploadStatus.UPLOADED,
                uploadedAtMillis = now - MetricsRepository.DEFAULT_RETENTION_MILLIS - 1,
            )
            dao.add(
                "new-uploaded",
                collectedAtMillis = 2L,
                status = UploadStatus.UPLOADED,
                uploadedAtMillis = now - 1_000L,
            )
            dao.add("pending", collectedAtMillis = 3L)
            val api = FakeTelemetryApi { BatchUploadResponse(accepted = listOf("pending")) }
            val engine = TelemetrySyncEngine(dao, api, clock = { now })

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(1, (result as SyncRunResult.Success).pruned)
            assertEquals(setOf("new-uploaded", "pending"), dao.records.map { it.id }.toSet())
            assertEquals(UploadStatus.UPLOADED, dao.records.first { it.id == "pending" }.uploadStatus)
        }
}

private class FakeTelemetryApi(
    private val handler: suspend (BatchUploadRequest) -> BatchUploadResponse,
) : TelemetryApi {
    val requests = mutableListOf<BatchUploadRequest>()

    override suspend fun uploadBatch(request: BatchUploadRequest): BatchUploadResponse {
        requests += request
        return handler(request)
    }
}

private fun httpError(code: Int): HttpException =
    HttpException(
        Response.error<BatchUploadResponse>(code, "".toResponseBody("application/json".toMediaType())),
    )
