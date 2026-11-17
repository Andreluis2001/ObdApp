package com.app.drivedata.sync

import com.app.drivedata.api.BatchUploadRequest
import com.app.drivedata.api.BatchUploadResponse
import com.app.drivedata.api.TelemetryApi
import com.app.drivedata.api.TelemetryReadingDto
import com.app.drivedata.api.TelemetryRecordDto
import com.app.drivedata.logging.AppLog
import com.app.drivedata.metrics.MetricsRepository
import com.app.drivedata.metrics.dao.MetricRecordDao
import com.app.drivedata.metrics.dao.MetricRecordWithReadings
import com.app.drivedata.metrics.model.MetricRecord
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.metrics.model.UploadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TelemetrySyncEngine(
    private val dao: MetricRecordDao,
    private val api: TelemetryApi,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retentionMillis: Long = MetricsRepository.DEFAULT_RETENTION_MILLIS,
) {

    private val mutex = Mutex()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    suspend fun sync(ownerUsername: String): SyncRunResult =
        mutex.withLock {
            _status.update { it.copy(isSyncing = true, lastError = null) }
            var error: SyncError? = null
            try {
                val result = runSync(ownerUsername)
                error = result.errorOrNull()
                result
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                AppLog.e("Telemetry sync failed unexpectedly", failure)
                error = SyncError.UNKNOWN
                SyncRunResult.Retryable("unexpected sync failure", SyncError.UNKNOWN)
            } finally {
                _status.update {
                    it.copy(
                        isSyncing = false,
                        lastError = error,
                        lastFinishedAtMillis = clock(),
                    )
                }
            }
        }

    private suspend fun runSync(ownerUsername: String): SyncRunResult {
        val pruned = dao.pruneUploadedBefore(clock() - retentionMillis)
        if (pruned > 0) AppLog.i("Pruned $pruned uploaded telemetry records past retention")

        var uploaded = 0
        var rejected = 0

        while (true) {
            val batch = dao.findWithReadingsByStatus(
                owner = ownerUsername,
                status = UploadStatus.PENDING,
                limit = TelemetryApi.BATCH_SIZE,
            )
            if (batch.isEmpty()) break

            val batchIds = batch.map { it.record.id }.toSet()
            when (val outcome = upload(batch, ownerUsername)) {
                is UploadOutcome.Processed -> {
                    val acknowledged = outcome.response.acknowledgedIds.filter { it in batchIds }.distinct()
                    val rejectedRecords = outcome.response.rejected.filter { it.id in batchIds }

                    if (acknowledged.isEmpty() && rejectedRecords.isEmpty()) {
                        dao.recordAttemptFailure(
                            ids = batchIds.toList(),
                            pendingStatus = UploadStatus.PENDING,
                            error = "server returned no acknowledgement",
                        )
                        return SyncRunResult.Retryable(
                            "server returned no acknowledgement",
                            SyncError.SERVER,
                        )
                    }

                    if (acknowledged.isNotEmpty()) {
                        dao.markUploaded(
                            ids = acknowledged,
                            status = UploadStatus.UPLOADED,
                            pendingStatus = UploadStatus.PENDING,
                            uploadedAtMillis = clock(),
                        )
                    }
                    rejectedRecords.forEach { record ->
                        dao.markRejected(
                            ids = listOf(record.id),
                            rejectedStatus = UploadStatus.REJECTED,
                            pendingStatus = UploadStatus.PENDING,
                            error = record.reason(),
                        )
                    }

                    uploaded += acknowledged.size
                    rejected += rejectedRecords.size
                    AppLog.i(
                        "Telemetry batch processed: acked=${acknowledged.size}, " +
                            "rejected=${rejectedRecords.size}, pendingBefore=${batch.size}",
                    )
                }

                is UploadOutcome.Retryable -> {
                    dao.recordAttemptFailure(
                        ids = batchIds.toList(),
                        pendingStatus = UploadStatus.PENDING,
                        error = outcome.reason,
                    )
                    AppLog.w("Telemetry batch will be retried: ${outcome.reason}")
                    return SyncRunResult.Retryable(outcome.reason, outcome.error)
                }

                is UploadOutcome.Failed -> {
                    AppLog.e("Telemetry batch failed permanently: ${outcome.reason}")
                    return SyncRunResult.Failed(outcome.reason, outcome.error)
                }
            }
        }

        return SyncRunResult.Success(uploaded = uploaded, rejected = rejected, pruned = pruned)
    }

    private suspend fun upload(
        batch: List<MetricRecordWithReadings>,
        ownerUsername: String,
    ): UploadOutcome =
        try {
            val request =
                BatchUploadRequest(
                    records = batch.map { it.toRecord().toDto(ownerUsername) },
                )
            UploadOutcome.Processed(api.uploadBatch(request))
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            error.toOutcome()
        } catch (error: IOException) {
            UploadOutcome.Retryable("network failure: ${error.message}", SyncError.NETWORK)
        } catch (error: Exception) {
            UploadOutcome.Retryable("unexpected upload failure: ${error.message}", SyncError.UNKNOWN)
        }

    private fun HttpException.toOutcome(): UploadOutcome =
        when (code()) {
            401, 403 -> UploadOutcome.Failed("authentication rejected (HTTP ${code()})", SyncError.AUTH)
            408, 429, 500, 502, 503, 504 ->
                UploadOutcome.Retryable("temporary server failure (HTTP ${code()})", SyncError.SERVER)
            in 500..599 ->
                UploadOutcome.Retryable("server failure (HTTP ${code()})", SyncError.SERVER)
            in 400..499 ->
                UploadOutcome.Failed("request rejected by the server (HTTP ${code()})", SyncError.SERVER)
            else -> UploadOutcome.Retryable("unexpected HTTP ${code()}", SyncError.SERVER)
        }

    private sealed interface UploadOutcome {
        data class Processed(val response: BatchUploadResponse) : UploadOutcome

        data class Retryable(val reason: String, val error: SyncError) : UploadOutcome

        data class Failed(val reason: String, val error: SyncError) : UploadOutcome
    }
}

private fun MetricRecord.toDto(ownerUsername: String): TelemetryRecordDto =
    TelemetryRecordDto(
        id = id,
        vin = vin,
        collectedAt = formatUtc(collectedAtMillis),
        user = ownerUsername,
        readings = readings.map { reading ->
            TelemetryReadingDto(
                metric = reading.id.wireName,
                unit = reading.unit,
                available = reading.value is ReadingValue.Available,
                value = (reading.value as? ReadingValue.Available)?.value,
            )
        },
    )

private fun SyncRunResult.errorOrNull(): SyncError? =
    when (this) {
        is SyncRunResult.Success -> null
        is SyncRunResult.Failed -> error
        is SyncRunResult.Retryable -> error
    }

internal fun formatUtc(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(timestampMillis))
