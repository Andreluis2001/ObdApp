package com.app.drivedata.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.logging.AppLog
import com.app.drivedata.metrics.ActiveVehicleCollection
import com.app.drivedata.metrics.CollectionStartResult
import com.app.drivedata.metrics.MetricsCollectionState
import com.app.drivedata.metrics.MetricsRepository
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.storage.StorageStats
import com.app.drivedata.sync.SyncError
import com.app.drivedata.sync.SyncStatus
import com.app.drivedata.vehicle.model.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val vehicleName: String = "",
    val hasVehicle: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val connection: ObdConnectionState = ObdConnectionState.Disconnected,
    val isConnected: Boolean = false,
    val isCollecting: Boolean = false,
    val snapshot: MetricsSnapshot? = null,
    val history: Map<MetricId, List<Double>> = emptyMap(),
    val lastSyncedAtMillis: Long? = null,
    val pendingCount: Int = 0,
    val rejectedCount: Int = 0,
    val internetAvailable: Boolean = false,
    val isSyncing: Boolean = false,
    val syncError: SyncError? = null,
    val storageUsedBytes: Long = 0L,
    val storageBudgetBytes: Long = 0L,
    val canCollect: Boolean = false,
    val hasAdapterConfig: Boolean = false,
    val isStartingCollection: Boolean = false,
    val collectionStartFailure: CollectionStartResult? = null,
) {
    val pendingBytes: Long
        get() = pendingCount * MetricsRepository.ESTIMATED_RECORD_BYTES

    val canSyncNow: Boolean
        get() = internetAvailable && !isSyncing && pendingCount > 0

    val canStartCollection: Boolean
        get() = canCollect && hasAdapterConfig && !isStartingCollection

    val isAdapterBusy: Boolean
        get() = connection is ObdConnectionState.Connecting ||
            connection is ObdConnectionState.Initializing
}

private data class CollectionActionState(
    val isStarting: Boolean = false,
    val failure: CollectionStartResult? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    activeVehicle: Flow<ActiveVehicleCollection?>,
    vehicles: Flow<List<Vehicle>>,
    connectionState: StateFlow<ObdConnectionState>,
    collectionState: StateFlow<MetricsCollectionState>,
    readingHistory: Flow<Map<MetricId, List<Double>>>,
    pendingCount: Flow<Int>,
    rejectedCount: Flow<Int>,
    lastSyncedAtMillis: Flow<Long?>,
    internetAvailable: Flow<Boolean>,
    syncStatus: StateFlow<SyncStatus>,
    storageStats: Flow<StorageStats>,
    private val onSyncNow: () -> Unit,
    private val startCollectionSession: suspend () -> CollectionStartResult = {
        CollectionStartResult.NO_ACTIVE_VEHICLE
    },
    private val stopCollectionSession: () -> Unit = {},
    private val activateVehicle: suspend (Vehicle) -> Boolean = { true },
) : ViewModel() {

    private val collectionAction = MutableStateFlow(CollectionActionState())
    private var collectionStartJob: Job? = null

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
        combine(
            coreState,
            internetAvailable,
            syncStatus,
            activeVehicle,
            vehicles,
        ) { core, internet, sync, vehicle, allVehicles ->
            HomeUiState(
                vehicleName = vehicle?.displayName.orEmpty(),
                hasVehicle = vehicle != null,
                vehicles = allVehicles,
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
                canCollect = vehicle?.canCollect == true,
                hasAdapterConfig = vehicle?.adapterConfig != null,
            )
        }.combine(storageStats) { state, storage ->
            state.copy(
                storageUsedBytes = storage.usedBytes,
                storageBudgetBytes = storage.budgetBytes,
            )
        }.combine(readingHistory) { state, history ->
            state.copy(history = history)
        }.combine(collectionAction) { state, action ->
            state.copy(
                isStartingCollection = action.isStarting,
                collectionStartFailure = if (state.isCollecting) null else action.failure,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(),
        )

    fun syncNow() {
        onSyncNow()
    }

    /** Explicit continue action: reuses the live link, reconnecting with saved IP/port if needed. */
    fun startCollection() {
        val current = uiState.value
        if (collectionAction.value.isStarting ||
            current.isCollecting ||
            !current.canStartCollection ||
            current.isAdapterBusy
        ) {
            return
        }

        collectionStartJob?.cancel()
        collectionStartJob =
            viewModelScope.launch {
                collectionAction.value = CollectionActionState(isStarting = true)
                val result =
                    try {
                        startCollectionSession()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLog.w("Collection could not be started from the dashboard", error)
                        CollectionStartResult.CONNECTION_FAILED
                    }
                collectionAction.value =
                    CollectionActionState(
                        isStarting = false,
                        failure = result.takeIf { it.isFailure && it != CollectionStartResult.ADAPTER_BUSY },
                    )
            }
    }

    /** Explicit pause action: stops polling and cancels a reconnect that is still in progress. */
    fun stopCollection() {
        collectionStartJob?.cancel()
        collectionStartJob = null
        collectionAction.value = CollectionActionState()
        stopCollectionSession()
    }

    fun selectVehicle(vehicleId: String) {
        val vehicle = uiState.value.vehicles.firstOrNull { it.id == vehicleId } ?: return
        viewModelScope.launch { activateVehicle(vehicle) }
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
                        activeVehicle = container.activeVehicleCollection,
                        vehicles = container.vehicleRepository.observeVehicles(),
                        connectionState = container.obdConnectionManager.state,
                        collectionState = container.metricsCollectionController.state,
                        readingHistory = container.metricsCollectionController.history,
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
                        startCollectionSession = container.metricsCollectionStarter::start,
                        stopCollectionSession = container.metricsCollectionStarter::stop,
                        activateVehicle = { vehicle ->
                            container.activeVehicleSwitcher.switchTo(vehicle)
                        },
                    )
                }
            }
    }
}

private fun ObdConnectionState.isEcuVerified(): Boolean =
    this is ObdConnectionState.Connected && ecuStatus == EcuStatus.VERIFIED
