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
import com.app.drivedata.sync.SyncError
import com.app.drivedata.sync.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val vehicleName: String = "",
    val connection: ObdConnectionState = ObdConnectionState.Disconnected,
    val isConnected: Boolean = false,
    val isCollecting: Boolean = false,
    val snapshot: MetricsSnapshot? = null,
    val lastSyncedAtMillis: Long? = null,
    val pendingCount: Int = 0,
    val rejectedCount: Int = 0,
    val internetAvailable: Boolean = false,
    val isSyncing: Boolean = false,
    val syncError: SyncError? = null,
    val storageUsedBytes: Long = 0L,
    val storageBudgetBytes: Long = 0L,
) {
    val pendingBytes: Long
        get() = pendingCount * ESTIMATED_RECORD_BYTES

    val canSyncNow: Boolean
        get() = internetAvailable && !isSyncing && pendingCount > 0

    companion object {
        const val ESTIMATED_RECORD_BYTES = 4L * 1024
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val vehicleName: String,
    connectionState: StateFlow<ObdConnectionState>,
    collectionState: StateFlow<MetricsCollectionState>,
    pendingCount: Flow<Int>,
    rejectedCount: Flow<Int>,
    lastSyncedAtMillis: Flow<Long?>,
    internetAvailable: Flow<Boolean>,
    syncStatus: StateFlow<SyncStatus>,
    storageStats: Flow<StorageStats>,
    private val onSyncNow: () -> Unit,
) : ViewModel() {

    private val coreState: Flow<CoreState> =
        combine(
            connectionState,
            collectionState,
            pendingCount,
            rejectedCount,
            lastSyncedAtMillis,
        ) { connection, collection, pending, rejected, lastSynced ->
            CoreState(
                connection = connection,
                collection = collection,
                pendingCount = pending,
                rejectedCount = rejected,
                lastSyncedAtMillis = lastSynced,
            )
        }

    val uiState: StateFlow<HomeUiState> =
        combine(coreState, internetAvailable, syncStatus) { core, internet, sync ->
            HomeUiState(
                vehicleName = vehicleName,
                connection = core.connection,
                isConnected = core.connection.isEcuVerified(),
                isCollecting = core.collection.isCollecting,
                snapshot = core.collection.snapshot,
                lastSyncedAtMillis = core.lastSyncedAtMillis,
                pendingCount = core.pendingCount,
                rejectedCount = core.rejectedCount,
                internetAvailable = internet,
                isSyncing = sync.isSyncing,
                syncError = sync.lastError,
            )
        }.combine(storageStats) { state, storage ->
            state.copy(
                storageUsedBytes = storage.usedBytes,
                storageBudgetBytes = storage.budgetBytes,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(vehicleName = vehicleName),
        )

    fun syncNow() {
        onSyncNow()
    }

    private data class CoreState(
        val connection: ObdConnectionState,
        val collection: MetricsCollectionState,
        val pendingCount: Int,
        val rejectedCount: Int,
        val lastSyncedAtMillis: Long?,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    val owner = container.authSession.signedInUser
                    HomeViewModel(
                        vehicleName = application.getString(R.string.home_default_vehicle),
                        connectionState = container.obdConnectionManager.state,
                        collectionState = container.metricsCollectionController.state,
                        pendingCount = owner.flatMapLatest { user ->
                            if (user == null) flowOf(0) else container.metricsRepository.observePendingCount(user)
                        },
                        rejectedCount = owner.flatMapLatest { user ->
                            if (user == null) flowOf(0) else container.metricsRepository.observeRejectedCount(user)
                        },
                        lastSyncedAtMillis = owner.flatMapLatest { user ->
                            if (user == null) {
                                flowOf(null)
                            } else {
                                container.metricsRepository.observeLastUploadedAtMillis(user)
                            }
                        },
                        internetAvailable = container.connectivityObserver.isAvailable,
                        syncStatus = container.telemetrySyncEngine.status,
                        storageStats = container.storageStatsProvider.observe(),
                        onSyncNow = container.syncScheduler::syncNow,
                    )
                }
            }
    }
}

private fun ObdConnectionState.isEcuVerified(): Boolean =
    this is ObdConnectionState.Connected && ecuStatus == EcuStatus.VERIFIED
