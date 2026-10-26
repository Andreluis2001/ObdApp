package com.app.drivedata.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.R
import com.app.drivedata.metrics.MetricsCollectionController
import com.app.drivedata.metrics.MetricsCollectionError
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Error surfaced next to the live readings; the connection screen reports link failures. */
enum class MetricsError {
    /** Readings could not be written to local storage; live values are still displayed. */
    STORAGE,

    /** Collection stopped because of an unexpected failure. */
    UNEXPECTED,
}

/** State rendered by the vehicle parameters screen. */
data class MetricsUiState(
    val vehicleName: String = "",
    val connection: ObdConnectionState = ObdConnectionState.Disconnected,
    val isCollecting: Boolean = false,
    val snapshot: MetricsSnapshot? = null,
    val history: Map<MetricId, List<Double>> = emptyMap(),
    val error: MetricsError? = null,
) {
    /** True only when the adapter answered the ECU, so live values are meaningful. */
    val isEcuVerified: Boolean
        get() = (connection as? ObdConnectionState.Connected)?.ecuStatus == EcuStatus.VERIFIED
}

/**
 * Reads the shared [MetricsCollectionController], which owns the collection loop for the whole
 * app, and exposes it to the parameters screen together with a short rolling history used by the
 * sparklines.
 */
class MetricsViewModel(
    private val vehicleName: String,
    connectionState: StateFlow<ObdConnectionState>,
    private val controller: MetricsCollectionController,
) : ViewModel() {

    private val history = MutableStateFlow<Map<MetricId, List<Double>>>(emptyMap())

    init {
        viewModelScope.launch {
            controller.state.collect { collection ->
                if (!collection.isCollecting) return@collect
                val snapshot = collection.snapshot
                if (snapshot == null) {
                    // A new collection session: drop samples from the previous one.
                    history.value = emptyMap()
                } else {
                    history.update { current -> current.appendedWith(snapshot) }
                }
            }
        }
    }

    val uiState: StateFlow<MetricsUiState> =
        combine(connectionState, controller.state, history) { connection, collection, points ->
            MetricsUiState(
                vehicleName = vehicleName,
                connection = connection,
                isCollecting = collection.isCollecting,
                snapshot = collection.snapshot,
                history = points,
                error = collection.error?.toUiError(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MetricsUiState(vehicleName = vehicleName),
        )

    fun startCollection() {
        controller.startCollection()
    }

    fun stopCollection() {
        controller.stopCollection()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MetricsViewModel(
                        vehicleName = application.getString(R.string.home_default_vehicle),
                        connectionState = application.container.obdConnectionManager.state,
                        controller = application.container.metricsCollectionController,
                    )
                }
            }
    }
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

private fun MetricsCollectionError.toUiError(): MetricsError =
    when (this) {
        MetricsCollectionError.STORAGE -> MetricsError.STORAGE
        MetricsCollectionError.UNEXPECTED -> MetricsError.UNEXPECTED
    }
