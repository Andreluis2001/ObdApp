package com.app.drivedata.sync

import com.app.drivedata.api.BatchUploadRequest
import com.app.drivedata.api.BatchUploadResponse
import com.app.drivedata.api.RejectedRecordDto
import com.app.drivedata.api.TelemetryApi
import com.app.drivedata.auth.RefreshResult
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
            val api = FakeTelemetryApi { _, _ -> receipt(accepted = 1) }
            val engine = TelemetrySyncEngine(dao, api, clock = { 10_000L })

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(1, (result as SyncRunResult.Success).uploaded)
            assertEquals(UploadStatus.UPLOADED, dao.records.single().uploadStatus)
            assertEquals(10_000L, dao.records.single().uploadedAtMillis)

            val request = api.requests.single()
            val record = request.records.single()
            assertEquals("record-1", record.id)
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
            val api = FakeTelemetryApi { _, _ -> receipt(accepted = 1) }

            TelemetrySyncEngine(dao, api).sync("admin")

            val fuel = api.requests.single().records.single().readings.first { it.metric == "fuel_level" }
            assertEquals(false, fuel.available)
            assertNull(fuel.value)
        }

    @Test
    fun sync_marksRecordsRejectedByTheServerAndRetainsThem() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api =
                FakeTelemetryApi { _, _ ->
                    receipt(
                        accepted = 0,
                        rejected =
                            listOf(
                                RejectedRecordDto(
                                    index = 0,
                                    id = "record-1",
                                    vin = "93YBSR7RHEJ298116",
                                    reason = "vehicle_not_authorized",
                                ),
                            ),
                    )
                }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(1, (result as SyncRunResult.Success).rejected)
            val record = dao.records.single()
            assertEquals(UploadStatus.REJECTED, record.uploadStatus)
            assertEquals("vehicle_not_authorized", record.lastError)
        }

    @Test
    fun sync_keepsRecordsPendingOnANetworkFailure() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { _, _ -> throw IOException("timeout") }
            val engine = TelemetrySyncEngine(dao, api)

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Retryable)
            val record = dao.records.single()
            assertEquals(UploadStatus.PENDING, record.uploadStatus)
            assertEquals(1, record.attemptCount)
            assertEquals(SyncError.NETWORK, engine.status.value.lastError)
        }

    @Test
    fun sync_refreshesTheTokenAndRetriesOnceOnUnauthorized() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api =
                FakeTelemetryApi { _, call ->
                    if (call == 1) throw httpError(401) else receipt(accepted = 1)
                }
            var refreshCalls = 0
            var reauthCalls = 0
            val engine =
                TelemetrySyncEngine(
                    dao,
                    api,
                    refreshAuth = { refreshCalls++; RefreshResult.Refreshed },
                    onReauthenticationRequired = { reauthCalls++ },
                )

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(2, api.requests.size)
            assertEquals(1, refreshCalls)
            assertEquals(0, reauthCalls)
            assertEquals(UploadStatus.UPLOADED, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_requiresReauthenticationWhenTheRefreshedTokenIsStillRejected() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { _, _ -> throw httpError(401) }
            var reauthCalls = 0
            val engine =
                TelemetrySyncEngine(
                    dao,
                    api,
                    refreshAuth = { RefreshResult.Refreshed },
                    onReauthenticationRequired = { reauthCalls++ },
                )

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Failed)
            assertEquals(SyncError.AUTH, (result as SyncRunResult.Failed).error)
            assertEquals(2, api.requests.size)
            assertEquals(1, reauthCalls)
            assertEquals(UploadStatus.PENDING, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_keepsRecordsPendingWhenTheRefreshTokenIsRejected() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { _, _ -> throw httpError(401) }
            var reauthCalls = 0
            val engine =
                TelemetrySyncEngine(
                    dao,
                    api,
                    refreshAuth = { RefreshResult.ReauthenticationRequired },
                    onReauthenticationRequired = { reauthCalls++ },
                )

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Failed)
            assertEquals(SyncError.AUTH, (result as SyncRunResult.Failed).error)
            assertEquals(1, api.requests.size)
            assertEquals(1, reauthCalls)
            assertEquals(UploadStatus.PENDING, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_keepsRecordsPendingWhenTokenRefreshHasNoConnectivity() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { _, _ -> throw httpError(401) }
            val engine =
                TelemetrySyncEngine(dao, api, refreshAuth = { RefreshResult.Unavailable })

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Retryable)
            assertEquals(SyncError.NETWORK, (result as SyncRunResult.Retryable).error)
            assertEquals(1, api.requests.size)
            assertEquals(UploadStatus.PENDING, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_doesNotRefreshOnForbiddenAndKeepsRecordsPending() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { _, _ -> throw httpError(403) }
            var refreshCalls = 0
            val engine =
                TelemetrySyncEngine(dao, api, refreshAuth = { refreshCalls++; RefreshResult.Refreshed })

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Failed)
            assertEquals(SyncError.AUTH, (result as SyncRunResult.Failed).error)
            assertEquals(0, refreshCalls)
            assertEquals(UploadStatus.PENDING, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_marksPermanentClientErrorsRejectedInsteadOfRetryingForever() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { _, _ -> throw httpError(422) }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

            assertTrue(result is SyncRunResult.Failed)
            assertEquals(SyncError.SERVER, (result as SyncRunResult.Failed).error)
            assertEquals(UploadStatus.REJECTED, dao.records.single().uploadStatus)
            assertEquals(1, api.requests.size)
        }

    @Test
    fun sync_pagesThroughTheQueueInBatches() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            repeat(150) { index -> dao.add("record-$index", collectedAtMillis = index.toLong()) }
            val api =
                FakeTelemetryApi { request, _ ->
                    receipt(accepted = request.records.size)
                }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

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
            val api =
                FakeTelemetryApi { request, _ ->
                    receipt(accepted = request.records.size)
                }

            TelemetrySyncEngine(dao, api).sync("admin")

            assertEquals(listOf("mine"), api.requests.single().records.map { it.id })
            assertEquals(UploadStatus.PENDING, dao.records.first { it.id == "other" }.uploadStatus)
        }

    @Test
    fun sync_doesNotLoopWhenTheServerAcknowledgesNothing() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { _, _ -> receipt(accepted = 0) }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

            assertTrue(result is SyncRunResult.Retryable)
            assertEquals(1, api.requests.size)
            assertEquals(UploadStatus.PENDING, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_keepsRecordsPendingWhenTheAcknowledgementCountDoesNotMatch() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.add("record-1", collectedAtMillis = 1_000L)
            val api = FakeTelemetryApi { _, _ -> receipt(accepted = 2) }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

            assertTrue(result is SyncRunResult.Retryable)
            assertEquals(1, api.requests.size)
            assertEquals(UploadStatus.PENDING, dao.records.single().uploadStatus)
        }

    @Test
    fun sync_rejectsRecordsWithoutAVinLocally() =
        runBlocking {
            val dao = FakeMetricRecordDao()
            dao.insertRecord(
                MetricRecordEntity(
                    id = "no-vin",
                    ownerUsername = "admin",
                    vin = null,
                    collectedAtMillis = 1_000L,
                    uploadStatus = UploadStatus.PENDING,
                ),
            )
            dao.add("with-vin", collectedAtMillis = 2_000L)
            val api =
                FakeTelemetryApi { request, _ ->
                    receipt(accepted = request.records.size)
                }

            val result = TelemetrySyncEngine(dao, api).sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(1, api.requests.single().records.size)
            assertEquals(UploadStatus.REJECTED, dao.records.first { it.id == "no-vin" }.uploadStatus)
            assertEquals(UploadStatus.UPLOADED, dao.records.first { it.id == "with-vin" }.uploadStatus)
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
            val api = FakeTelemetryApi { _, _ -> receipt(accepted = 1) }
            val engine = TelemetrySyncEngine(dao, api, clock = { now })

            val result = engine.sync("admin")

            assertTrue(result is SyncRunResult.Success)
            assertEquals(1, (result as SyncRunResult.Success).pruned)
            assertEquals(setOf("new-uploaded", "pending"), dao.records.map { it.id }.toSet())
            assertEquals(UploadStatus.UPLOADED, dao.records.first { it.id == "pending" }.uploadStatus)
        }
}

private fun receipt(
    accepted: Int,
    rejected: List<RejectedRecordDto> = emptyList(),
) = BatchUploadResponse(accepted = accepted, rejected = rejected, receivedAt = "2026-11-17T20:29:47.947Z")

private class FakeTelemetryApi(
    private val handler: suspend (BatchUploadRequest, Int) -> BatchUploadResponse,
) : TelemetryApi {
    val requests = mutableListOf<BatchUploadRequest>()

    override suspend fun uploadBatch(request: BatchUploadRequest): BatchUploadResponse {
        requests += request
        return handler(request, requests.size)
    }
}

private fun httpError(code: Int): HttpException =
    HttpException(
        Response.error<BatchUploadResponse>(code, "".toResponseBody("application/json".toMediaType())),
    )
