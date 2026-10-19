package com.app.drivedata.metrics

import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.github.eltonvs.obd.command.BadResponseException
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.control.VINCommand
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.IOException

/**
 * Reads the configured OBD-II parameters one after another over the single active adapter link.
 *
 * Each sweep issues every command sequentially (never concurrently) and maps `NO DATA`, unsupported
 * parameters, and adapter errors to [ReadingValue.Unavailable] instead of a numeric zero. The flow
 * ends as soon as the connection is no longer [ObdConnectionState.Connected], so callers can rely on
 * it to stop on disconnect.
 *
 * Runs on the caller's dispatcher/scope; the app drives it from a ViewModel so there is exactly one
 * collection loop at a time and no `GlobalScope`.
 */
class ObdMetricsCollector(
    private val connectionManager: ObdConnectionManager,
    private val catalog: List<MetricDefinition> = defaultMetricCatalog,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Emits one snapshot per sequential sweep until the connection closes or the collector is
     * cancelled. VIN is read once up front and reused for every snapshot.
     */
    fun stream(): Flow<MetricsSnapshot> =
        flow {
            val vin = readVin()
            while (currentCoroutineContext().isActive) {
                if (connectionManager.state.value !is ObdConnectionState.Connected) break
                emit(readSnapshot(vin))
                delay(pollIntervalMs)
            }
        }

    private suspend fun readSnapshot(vin: String?): MetricsSnapshot {
        val readings = ArrayList<MetricReading>(catalog.size)
        for (definition in catalog) {
            readings += readMetric(definition)
        }
        return MetricsSnapshot(
            timestampMillis = clock(),
            vin = vin,
            readings = readings,
        )
    }

    private suspend fun readMetric(definition: MetricDefinition): MetricReading =
        MetricReading(
            id = definition.id,
            unit = definition.unit,
            value = readSafely(definition.commandFactory())?.value?.toDoubleOrNull()
                .let { if (it != null) ReadingValue.Available(it) else ReadingValue.Unavailable },
        )

    private suspend fun readVin(): String? =
        readSafely(VINCommand())?.value?.trim()?.ifBlank { null }

    /**
     * Runs a command and returns its response, mapping ECU/adapter errors to null so the caller can
     * treat the parameter as unavailable. Transport-level [IOException]s are treated the same way
     * here: the collection loop simply reports the value as unavailable rather than crashing.
     */
    private suspend fun readSafely(command: ObdCommand): ObdResponse? =
        try {
            connectionManager.runCommand(command)
        } catch (_: BadResponseException) {
            null
        } catch (_: IOException) {
            null
        }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L
    }
}
