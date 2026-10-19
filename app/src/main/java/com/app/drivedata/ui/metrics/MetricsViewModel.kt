package com.app.drivedata.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.ObdMetricsCollector
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State rendered by the live-readings screen. */
data class MetricsUiState(
    val connection: ObdConnectionState = ObdConnectionState.Disconnected,
    val isCollecting: Boolean = false,
    val snapshot: MetricsSnapshot? = null,
)

/**
 * Coordinates one sequential collection loop for the metrics screen.
 *
 * The loop lives in [viewModelScope] and stops on its own when the connection leaves
 * [ObdConnectionState.Connected], so there is never more than one active collector.
 */
class MetricsViewModel(
    private val collector: ObdMetricsCollector,
    private val connectionManager: ObdConnectionManager,
) : ViewModel() {

    private val isCollecting = MutableStateFlow(false)
    private val snapshot = MutableStateFlow<MetricsSnapshot?>(null)
    private var collectionJob: Job? = null

    val uiState: StateFlow<MetricsUiState> =
        combine(connectionManager.state, isCollecting, snapshot) { connection, collecting, current ->
            MetricsUiState(
                connection = connection,
                isCollecting = collecting,
                snapshot = current,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MetricsUiState(),
        )

    /** Starts collecting when the ECU is verified; a no-op while already collecting or disconnected. */
    fun startCollection() {
        if (collectionJob?.isActive == true) return
        val connection = connectionManager.state.value
        if (connection !is ObdConnectionState.Connected || connection.ecuStatus != EcuStatus.VERIFIED) return

        isCollecting.value = true
        collectionJob =
            viewModelScope.launch {
                collector.stream().collect { snapshot.value = it }
                isCollecting.value = false
            }
    }

    fun stopCollection() {
        collectionJob?.cancel()
        collectionJob = null
        isCollecting.value = false
    }

    override fun onCleared() {
        stopCollection()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MetricsViewModel(
                        collector = application.container.metricsCollector,
                        connectionManager = application.container.obdConnectionManager,
                    )
                }
            }
    }
}
