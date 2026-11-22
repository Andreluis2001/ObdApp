package com.app.drivedata.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.ObdMetricsCollector
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.sync.SyncError
import com.app.drivedata.sync.SyncStatus
import com.app.drivedata.vehicle.model.Vehicle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val username: String = "",
    val displayName: String = "",
    val email: String? = null,
    val vehicleCount: Int = 0,
    val pendingCount: Int = 0,
    val rejectedCount: Int = 0,
    val lastSyncedAtMillis: Long? = null,
    val syncError: SyncError? = null,
    val adapterConnected: Boolean = false,
    val internetAvailable: Boolean = false,
    val collectionIntervalSeconds: Long = DEFAULT_COLLECTION_INTERVAL_SECONDS,
) {
    companion object {
        const val DEFAULT_COLLECTION_INTERVAL_SECONDS: Long =
            ObdMetricsCollector.DEFAULT_POLL_INTERVAL_MS / 1_000L
    }
}

/**
 * Supplies the profile tab with the signed-in identity, garage size and live adapter/internet
 * status. The backend only stores a username, so the display name and e-mail are derived from it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    signedInUser: StateFlow<String?>,
    vehicles: Flow<List<Vehicle>>,
    connectionState: StateFlow<ObdConnectionState>,
    pendingCount: Flow<Int>,
    rejectedCount: Flow<Int>,
    lastSyncedAtMillis: Flow<Long?>,
    internetAvailable: Flow<Boolean>,
    syncStatus: StateFlow<SyncStatus>,
    private val collectionIntervalSeconds: Long = ProfileUiState.DEFAULT_COLLECTION_INTERVAL_SECONDS,
) : ViewModel() {

    private val identity: Flow<ProfileIdentity> =
        signedInUser.map { raw ->
            val username = raw.orEmpty()
            ProfileIdentity(
                username = username,
                displayName = displayNameFrom(username),
                email = emailFrom(username),
            )
        }

    private val sync: Flow<ProfileSync> =
        combine(
            pendingCount,
            rejectedCount,
            lastSyncedAtMillis,
            syncStatus,
        ) { pending, rejected, lastSync, status ->
            ProfileSync(pending, rejected, lastSync, status.lastError)
        }

    private val connectivity: Flow<ProfileConnectivity> =
        combine(connectionState, internetAvailable) { connection, internet ->
            ProfileConnectivity(
                adapterConnected = connection is ObdConnectionState.Connected,
                internetAvailable = internet,
            )
        }

    val uiState: StateFlow<ProfileUiState> =
        combine(identity, vehicles, sync, connectivity) { id, allVehicles, syncInfo, connectionInfo ->
            ProfileUiState(
                username = id.username,
                displayName = id.displayName,
                email = id.email,
                vehicleCount = allVehicles.size,
                pendingCount = syncInfo.pendingCount,
                rejectedCount = syncInfo.rejectedCount,
                lastSyncedAtMillis = syncInfo.lastSyncedAtMillis,
                syncError = syncInfo.syncError,
                adapterConnected = connectionInfo.adapterConnected,
                internetAvailable = connectionInfo.internetAvailable,
                collectionIntervalSeconds = collectionIntervalSeconds,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ProfileUiState(),
        )

    private data class ProfileIdentity(
        val username: String,
        val displayName: String,
        val email: String?,
    )

    private data class ProfileSync(
        val pendingCount: Int,
        val rejectedCount: Int,
        val lastSyncedAtMillis: Long?,
        val syncError: SyncError?,
    )

    private data class ProfileConnectivity(
        val adapterConnected: Boolean,
        val internetAvailable: Boolean,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        @OptIn(ExperimentalCoroutinesApi::class)
        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    val owner = container.authSession.signedInUser
                    ProfileViewModel(
                        signedInUser = owner,
                        vehicles = container.vehicleRepository.observeVehicles(),
                        connectionState = container.obdConnectionManager.state,
                        pendingCount =
                            owner.flatMapLatest { user ->
                                if (user == null) {
                                    flowOf(0)
                                } else {
                                    container.metricsRepository.observePendingCount(user)
                                }
                            },
                        rejectedCount =
                            owner.flatMapLatest { user ->
                                if (user == null) {
                                    flowOf(0)
                                } else {
                                    container.metricsRepository.observeRejectedCount(user)
                                }
                            },
                        lastSyncedAtMillis =
                            owner.flatMapLatest { user ->
                                if (user == null) {
                                    flowOf(null)
                                } else {
                                    container.metricsRepository.observeLastUploadedAtMillis(user)
                                }
                            },
                        internetAvailable = container.connectivityObserver.isAvailable,
                        syncStatus = container.telemetrySyncEngine.status,
                    )
                }
            }
    }
}

/** `andre.vieira@empresa.com` is shown as the e-mail; plain usernames have no e-mail line. */
internal fun emailFrom(username: String): String? =
    username.trim().takeIf { it.contains('@') }

/** Turns a login identifier into a readable name: `andre.vieira` becomes `Andre Vieira`. */
internal fun displayNameFrom(username: String): String {
    val trimmed = username.trim()
    val localPart = trimmed.substringBefore('@').ifBlank { trimmed }
    return localPart
        .split('.', '_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase() } }
        .ifBlank { trimmed }
}
