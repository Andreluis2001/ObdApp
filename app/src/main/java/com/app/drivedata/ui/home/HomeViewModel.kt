package com.app.drivedata.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.R
import com.app.drivedata.metrics.MetricsCollectionState
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.storage.StorageStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** State rendered by the dashboard. */
data class HomeUiState(
    val vehicleName: String = "",
    val connection: ObdConnectionState = ObdConnectionState.Disconnected,
    val isConnected: Boolean = false,
    val isCollecting: Boolean = false,
    val snapshot: MetricsSnapshot? = null,
    val lastCollectedAtMillis: Long? = null,
    val pendingCount: Int = 0,
    val internetAvailable: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val storageBudgetBytes: Long = 0L,
) {
    /** Approximate queue size, mirroring the dashboard estimate. */
    val pendingBytes: Long
        get() = pendingCount * ESTIMATED_RECORD_BYTES

    companion object {
        const val ESTIMATED_RECORD_BYTES = 4L * 1024
    }
}

class HomeViewModel(
    private val vehicleName: String,
    connectionState: StateFlow<ObdConnectionState>,
    collectionState: StateFlow<MetricsCollectionState>,
    pendingCount: Flow<Int>,
    lastCollectedAtMillis: Flow<Long?>,
    internetAvailable: Flow<Boolean>,
    storageStats: Flow<StorageStats>,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(
            connectionState,
            collectionState,
            pendingCount,
            lastCollectedAtMillis,
            internetAvailable,
        ) { connection, collection, pending, lastSync, internet ->
            CoreState(
                connection = connection,
                collection = collection,
                pendingCount = pending,
                lastCollectedAtMillis = lastSync,
                internetAvailable = internet,
            )
        }.combine(storageStats) { core, storage ->
            HomeUiState(
                vehicleName = vehicleName,
                connection = core.connection,
                isConnected = core.connection.isEcuVerified(),
                isCollecting = core.collection.isCollecting,
                snapshot = core.collection.snapshot,
                lastCollectedAtMillis = core.lastCollectedAtMillis,
                pendingCount = core.pendingCount,
                internetAvailable = core.internetAvailable,
                storageUsedBytes = storage.usedBytes,
                storageBudgetBytes = storage.budgetBytes,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(vehicleName = vehicleName),
        )

    private data class CoreState(
        val connection: ObdConnectionState,
        val collection: MetricsCollectionState,
        val pendingCount: Int,
        val lastCollectedAtMillis: Long?,
        val internetAvailable: Boolean,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    HomeViewModel(
                        vehicleName = application.getString(R.string.home_default_vehicle),
                        connectionState = container.obdConnectionManager.state,
                        collectionState = container.metricsCollectionController.state,
                        pendingCount = container.metricsRepository.observePendingCount(),
                        lastCollectedAtMillis = container.metricsRepository.observeLastCollectedAtMillis(),
                        internetAvailable = container.connectivityObserver.isAvailable,
                        storageStats = container.storageStatsProvider.observe(),
                    )
                }
            }
    }
}

private fun ObdConnectionState.isEcuVerified(): Boolean =
    this is ObdConnectionState.Connected && ecuStatus == EcuStatus.VERIFIED
