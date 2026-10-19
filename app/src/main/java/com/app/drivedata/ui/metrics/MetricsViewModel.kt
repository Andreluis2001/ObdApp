package com.app.drivedata.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.MetricsCollectionController
import com.app.drivedata.metrics.MetricsCollectionError
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.ObdConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Error surfaced next to the live readings; the connection screen reports link failures. */
enum class MetricsError {
    /** Readings could not be written to local storage; live values are still displayed. */
    STORAGE,

    /** Collection stopped because of an unexpected failure. */
    UNEXPECTED,
}

/** State rendered by the live-readings screen. */
data class MetricsUiState(
    val connection: ObdConnectionState = ObdConnectionState.Disconnected,
    val isCollecting: Boolean = false,
    val snapshot: MetricsSnapshot? = null,
    val error: MetricsError? = null,
)

/**
 * Reads the shared [MetricsCollectionController], which owns the collection loop for the whole
 * app, and exposes it to the readings screen.
 */
class MetricsViewModel(
    private val connectionManager: ObdConnectionManager,
    private val controller: MetricsCollectionController,
) : ViewModel() {

    val uiState: StateFlow<MetricsUiState> =
        combine(connectionManager.state, controller.state) { connection, collection ->
            MetricsUiState(
                connection = connection,
                isCollecting = collection.isCollecting,
                snapshot = collection.snapshot,
                error = collection.error?.toUiError(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MetricsUiState(),
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
                        connectionManager = application.container.obdConnectionManager,
                        controller = application.container.metricsCollectionController,
                    )
                }
            }
    }
}

private fun MetricsCollectionError.toUiError(): MetricsError =
    when (this) {
        MetricsCollectionError.STORAGE -> MetricsError.STORAGE
        MetricsCollectionError.UNEXPECTED -> MetricsError.UNEXPECTED
    }
