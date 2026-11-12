package com.app.drivedata.metrics

import com.app.drivedata.auth.AuthSession
import com.app.drivedata.logging.AppLog
import com.app.drivedata.metrics.model.MetricsSnapshot
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
import kotlinx.coroutines.launch
import java.io.IOException

enum class MetricsCollectionError {
    STORAGE,
    UNEXPECTED,
}

data class MetricsCollectionState(
    val isCollecting: Boolean = false,
    val snapshot: MetricsSnapshot? = null,
    val error: MetricsCollectionError? = null,
)

class MetricsCollectionController(
    private val scope: CoroutineScope,
    private val connectionState: StateFlow<ObdConnectionState>,
    private val streamFactory: () -> Flow<MetricsSnapshot>,
    private val repository: MetricsRepository,
    private val authSession: AuthSession,
    private val onSnapshotPersisted: () -> Unit = {},
) {

    private val _state = MutableStateFlow(MetricsCollectionState())
    val state: StateFlow<MetricsCollectionState> = _state.asStateFlow()

    private var collectionJob: Job? = null
    
    private var collectionSession = 0

    @Volatile private var stoppedByUser = false

    @Volatile private var maintenancePaused = false

    init {
        scope.launch {
            connectionState.collect { connection ->
                if (connection.isCollectible()) {
                    if (!stoppedByUser && !maintenancePaused) startCollection()
                } else {
                    if (!maintenancePaused) stoppedByUser = false
                    stopInternal()
                    if (_state.value.snapshot != null) {
                        _state.value = _state.value.copy(snapshot = null)
                    }
                }
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
        if (!stoppedByUser) startCollection()
    }

    fun startCollection() {
        if (collectionJob?.isActive == true) return
        if (!connectionState.value.isCollectible()) return

        stoppedByUser = false
        val session = ++collectionSession
        AppLog.i("Metrics collection started (session=$session)")
        _state.value = MetricsCollectionState(isCollecting = true)
        collectionJob =
            scope.launch {
                try {
                    streamFactory().collect { snapshot ->
                        persist(snapshot)
                        _state.value = _state.value.copy(snapshot = snapshot)
                        AppLog.d(
                            "Metrics snapshot applied to UI: ${snapshot.readings.size} readings, " +
                                "timestamp=${snapshot.timestampMillis}",
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
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

    fun stopCollection() {
        stoppedByUser = true
        stopInternal()
    }

    private fun stopInternal() {
        collectionSession++
        collectionJob?.cancel()
        collectionJob = null
        AppLog.i("Metrics collection stopped")
        _state.value = _state.value.copy(isCollecting = false)
    }

    private suspend fun persist(snapshot: MetricsSnapshot) {
        val owner = authSession.signedInUser.value
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

private fun ObdConnectionState.isCollectible(): Boolean =
    this is ObdConnectionState.Connected && ecuStatus == EcuStatus.VERIFIED
