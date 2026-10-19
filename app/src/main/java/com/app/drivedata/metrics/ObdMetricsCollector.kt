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


class ObdMetricsCollector(
    private val connectionManager: ObdConnectionManager,
    private val catalog: List<MetricDefinition> = defaultMetricCatalog,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
 
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
 
    private suspend fun readSafely(command: ObdCommand): ObdResponse? =
        try {
            connectionManager.runCommand(command)
        } catch (_: BadResponseException) {
            null
        }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L
    }
}
