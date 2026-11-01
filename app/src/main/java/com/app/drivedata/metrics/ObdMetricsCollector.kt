package com.app.drivedata.metrics

import com.app.drivedata.logging.AppLog
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.EcuStatus
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
            if (!connectionManager.state.value.isVerified()) {
                AppLog.w("OBD metric stream not started: connection is ${connectionManager.state.value}")
                return@flow
            }
            val vin = readVin()
            AppLog.i("OBD metric stream started (vin=${vin ?: "unknown"}, metrics=${catalog.size})")
            try {
                while (currentCoroutineContext().isActive) {
                    if (!connectionManager.state.value.isVerified()) {
                        AppLog.w("OBD metric stream stopping: ${connectionManager.state.value}")
                        break
                    }
                    val snapshot = readSnapshot(vin) ?: break
                    emit(snapshot)
                    delay(pollIntervalMs)
                }
            } finally {
                AppLog.i("OBD metric stream stopped")
            }
        }

    private suspend fun readSnapshot(vin: String?): MetricsSnapshot? {
        val readings = ArrayList<MetricReading>(catalog.size)
        for (definition in catalog) {
            readings += readMetric(definition)
        }
        val available = readings.count { it.value is ReadingValue.Available }
        if (available == 0) {
            AppLog.w("OBD snapshot discarded: no metric was answered by the ECU")
            connectionManager.reportEcuNoResponse()
            return null
        }
        AppLog.d(
            "OBD snapshot received: vin=${vin ?: "unknown"}, available=$available/${readings.size}, " +
                readings.joinToString { "${it.id}=${it.value.short()}" },
        )
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
        } catch (error: BadResponseException) {
            AppLog.d("OBD '${command.rawCommand}' returned no usable data: $error")
            null
        } catch (error: IllegalArgumentException) {
            AppLog.d("OBD '${command.rawCommand}' returned a malformed value: $error")
            null
        } catch (error: IndexOutOfBoundsException) {
            AppLog.d("OBD '${command.rawCommand}' returned a truncated value: $error")
            null
        }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L
    }
}

private fun ObdConnectionState.isVerified(): Boolean =
    this is ObdConnectionState.Connected && ecuStatus == EcuStatus.VERIFIED

private fun ReadingValue.short(): String =
    when (this) {
        is ReadingValue.Available -> value.toString()
        ReadingValue.Unavailable -> "n/a"
    }
