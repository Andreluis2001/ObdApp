package com.app.drivedata.metrics

import com.app.drivedata.logging.AppLog
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class MetricsCollectionError {
    STORAGE,
    VIN_MISMATCH,
    UNEXPECTED,
}

/** Identity conflict that stopped a collection session; [reportedVin] is null if the ECU said nothing. */
data class VinMismatch(
    val expectedVin: String,
    val reportedVin: String?,
)

data class MetricsCollectionState(
    val isCollecting: Boolean = false,
    val snapshot: MetricsSnapshot? = null,
    val error: MetricsCollectionError? = null,
    val vinMismatch: VinMismatch? = null,
)

class MetricsCollectionController(
    private val scope: CoroutineScope,
    private val connectionState: StateFlow<ObdConnectionState>,
    private val activeVehicle: StateFlow<ActiveVehicleCollection?>,
    private val streamFactory: (String?, List<MetricDefinition>) -> Flow<MetricsSnapshot>,
    private val repository: MetricsRepository,
    private val signedInUser: StateFlow<String?>,
    private val disconnect: suspend () -> Unit = {},
    private val onSnapshotPersisted: () -> Unit = {},
) {

    private val _state = MutableStateFlow(MetricsCollectionState())
    val state: StateFlow<MetricsCollectionState> = _state.asStateFlow()

    private val _history = MutableStateFlow<Map<MetricId, List<Double>>>(emptyMap())

    /**
     * Recent readings per metric, accumulated while a collection session is running.
     * Shared by every screen that draws sparklines so they all see the same series.
     */
    val history: StateFlow<Map<MetricId, List<Double>>> = _history.asStateFlow()

    private var collectionJob: Job? = null

    private var collectionSession = 0

    @Volatile private var stoppedByUser = false

    @Volatile private var maintenancePaused = false

    private var runningConfig: ActiveVehicleCollection? = null

    private var lastVehicleId: String? = null

    init {
        scope.launch {
            combine(connectionState, activeVehicle) { connection, vehicle ->
                connection to vehicle
            }.collect { (connection, vehicle) ->
                onCollectionInputsChanged(connection, vehicle)
            }
        }
    }

    /**
     * Stops collection before an adapter setup/rediscovery sequence without marking it as a
     * user-initiated stop. The connection-state collector will not restart collection until
     * [resumeAfterMaintenance] is called.
     */
    fun pauseForMaintenance() {
        maintenancePaused = true
        stopInternal()
    }

    /** Allows collection to restart after a setup sequence, unless the user stopped it manually. */
    fun resumeAfterMaintenance() {
        maintenancePaused = false
        if (!stoppedByUser) startIfEligible()
    }

    /** Explicit user action: clears a previous pause and starts when the active vehicle allows it. */
    fun startCollection() {
        stoppedByUser = false
        if (maintenancePaused) return
        startIfEligible()
    }

    fun stopCollection() {
        stoppedByUser = true
        stopInternal()
    }

    private fun onCollectionInputsChanged(
        connection: ObdConnectionState,
        vehicle: ActiveVehicleCollection?,
    ) {
        if (vehicle?.vehicleId != lastVehicleId) {
            lastVehicleId = vehicle?.vehicleId
            clearSnapshot()
            clearHistory()
            clearVinMismatch()
        }

        if (!isCollectible(connection, vehicle)) {
            if (!maintenancePaused) stoppedByUser = false
            stopInternal()
            runningConfig = null
            clearSnapshot()
            clearHistory()
            return
        }

        if (maintenancePaused) {
            stopInternal()
            return
        }

        if (stoppedByUser) {
            stopInternal()
            return
        }

        if (collectionJob?.isActive == true && runningConfig == vehicle) return

        stopInternal()
        startInternal(vehicle!!)
    }

    private fun startIfEligible() {
        val vehicle = activeVehicle.value ?: return
        if (!isCollectible(connectionState.value, vehicle)) return
        startInternal(vehicle)
    }

    private fun startInternal(vehicle: ActiveVehicleCollection) {
        if (collectionJob?.isActive == true) return
        if (!isCollectible(connectionState.value, vehicle)) return

        runningConfig = vehicle
        val session = ++collectionSession
        AppLog.i(
            "Metrics collection started (session=$session, vehicle=${vehicle.vehicleId}, " +
                "pids=${vehicle.catalog.size})",
        )
        _state.value = MetricsCollectionState(isCollecting = true)
        collectionJob =
            scope.launch {
                try {
                    streamFactory(vehicle.vin, vehicle.catalog).collect { snapshot ->
                        persist(snapshot)
                        _state.value = _state.value.copy(snapshot = snapshot)
                        _history.update { current -> current.appendedWith(snapshot) }
                        AppLog.d(
                            "Metrics snapshot applied to UI: ${snapshot.readings.size} readings, " +
                                "timestamp=${snapshot.timestampMillis}",
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (mismatch: VinMismatchException) {
                    // Set the warning before dropping the link: disconnecting updates the
                    // connection flow, which stops this job and may cancel it mid-catch.
                    AppLog.e(
                        "VIN mismatch: selected='${mismatch.expectedVin}', " +
                            "reported='${mismatch.reportedVin ?: "none"}'; disconnecting",
                    )
                    _state.value =
                        _state.value.copy(
                            error = MetricsCollectionError.VIN_MISMATCH,
                            vinMismatch =
                                VinMismatch(
                                    expectedVin = mismatch.expectedVin,
                                    reportedVin = mismatch.reportedVin,
                                ),
                        )
                    try {
                        disconnect()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLog.w("Could not close the adapter after a VIN mismatch", error)
                    }
                } catch (error: IOException) {
                    AppLog.w("Metrics collection stopped by an I/O error", error)
                } catch (error: Exception) {
                    AppLog.e("Metrics collection failed unexpectedly", error)
                    _state.value = _state.value.copy(error = MetricsCollectionError.UNEXPECTED)
                } finally {
                    if (session == collectionSession) {
                        _state.value = _state.value.copy(isCollecting = false)
                    }
                }
            }
    }

    private fun stopInternal() {
        collectionSession++
        collectionJob?.cancel()
        collectionJob = null
        AppLog.i("Metrics collection stopped")
        _state.value = _state.value.copy(isCollecting = false)
    }

    private fun clearSnapshot() {
        if (_state.value.snapshot != null) {
            _state.value = _state.value.copy(snapshot = null)
        }
    }

    private fun clearHistory() {
        _history.value = emptyMap()
    }

    private fun clearVinMismatch() {
        val current = _state.value
        if (current.vinMismatch == null && current.error != MetricsCollectionError.VIN_MISMATCH) return
        _state.value =
            current.copy(
                vinMismatch = null,
                error = if (current.error == MetricsCollectionError.VIN_MISMATCH) null else current.error,
            )
    }

    /** Acknowledges the VIN warning shown to the user. */
    fun dismissVinMismatch() = clearVinMismatch()

    private suspend fun persist(snapshot: MetricsSnapshot) {
        val owner = signedInUser.value
        if (owner == null) {
            AppLog.w("Metrics snapshot not persisted: no signed-in user")
            return
        }
        try {
            repository.save(snapshot, owner)
            if (_state.value.error == MetricsCollectionError.STORAGE) {
                _state.value = _state.value.copy(error = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            AppLog.e("Metrics snapshot could not be persisted", error)
            _state.value = _state.value.copy(error = MetricsCollectionError.STORAGE)
            return
        }
        runCatching { onSnapshotPersisted() }
            .onFailure { AppLog.w("Telemetry sync could not be scheduled", it) }
    }
}

private fun isCollectible(
    connection: ObdConnectionState,
    vehicle: ActiveVehicleCollection?,
): Boolean {
    if (vehicle == null || !vehicle.hasDiscovery || vehicle.catalog.isEmpty()) return false
    if (connection !is ObdConnectionState.Connected) return false
    if (connection.ecuStatus != EcuStatus.VERIFIED) return false
    return vehicle.matches(connection.config)
}

private const val MAX_HISTORY_POINTS = 40

private fun Map<MetricId, List<Double>>.appendedWith(
    snapshot: MetricsSnapshot,
): Map<MetricId, List<Double>> {
    val updated = HashMap<MetricId, List<Double>>(size + snapshot.readings.size)
    updated.putAll(this)
    snapshot.readings.forEach { reading ->
        val available = reading.value as? ReadingValue.Available ?: return@forEach
        updated[reading.id] =
            (updated[reading.id].orEmpty() + available.value).takeLast(MAX_HISTORY_POINTS)
    }
    return updated
}
