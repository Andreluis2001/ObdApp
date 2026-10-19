package com.app.drivedata.metrics

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

/**
 * Owns the single live-collection loop for the whole app.
 *
 * Collection starts automatically once the ECU is verified and stops when the link is lost.
 * A manual stop is remembered until the next successful connection, so the dashboard does not
 * immediately restart a loop the user just stopped.
 */
class MetricsCollectionController(
    private val scope: CoroutineScope,
    private val connectionState: StateFlow<ObdConnectionState>,
    private val streamFactory: () -> Flow<MetricsSnapshot>,
    private val repository: MetricsRepository,
) {

    private val _state = MutableStateFlow(MetricsCollectionState())
    val state: StateFlow<MetricsCollectionState> = _state.asStateFlow()

    private var collectionJob: Job? = null

    /**
     * Identifies the current collection run. A cancelled run that is still unwinding a blocking
     * socket read must not clear the state of a newer run.
     */
    private var collectionSession = 0

    private var stoppedByUser = false

    init {
        scope.launch {
            connectionState.collect { connection ->
                if (connection.isCollectible()) {
                    if (!stoppedByUser) startCollection()
                } else {
                    stoppedByUser = false
                    stopInternal()
                }
            }
        }
    }

    /** Starts collecting when the ECU is verified; a no-op while already collecting. */
    fun startCollection() {
        if (collectionJob?.isActive == true) return
        if (!connectionState.value.isCollectible()) return

        stoppedByUser = false
        val session = ++collectionSession
        _state.value = MetricsCollectionState(isCollecting = true)
        collectionJob =
            scope.launch {
                try {
                    streamFactory().collect { snapshot ->
                        persist(snapshot)
                        _state.value = _state.value.copy(snapshot = snapshot)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    // ObdConnectionManager already closed the link and reported Failed(TRANSPORT).
                } catch (_: Exception) {
                    _state.value = _state.value.copy(error = MetricsCollectionError.UNEXPECTED)
                } finally {
                    if (session == collectionSession) {
                        _state.value = _state.value.copy(isCollecting = false)
                    }
                }
            }
    }

    /** Stops the loop on explicit user request, until the next connection. */
    fun stopCollection() {
        stoppedByUser = true
        stopInternal()
    }

    private fun stopInternal() {
        collectionSession++
        collectionJob?.cancel()
        collectionJob = null
        _state.value = _state.value.copy(isCollecting = false)
    }

    /**
     * Stores one snapshot. A storage failure is surfaced but does not stop live collection: the
     * user still gets readings, and a later successful write clears the warning.
     */
    private suspend fun persist(snapshot: MetricsSnapshot) {
        try {
            repository.save(snapshot)
            if (_state.value.error == MetricsCollectionError.STORAGE) {
                _state.value = _state.value.copy(error = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = _state.value.copy(error = MetricsCollectionError.STORAGE)
        }
    }
}

private fun ObdConnectionState.isCollectible(): Boolean =
    this is ObdConnectionState.Connected && ecuStatus == EcuStatus.VERIFIED
