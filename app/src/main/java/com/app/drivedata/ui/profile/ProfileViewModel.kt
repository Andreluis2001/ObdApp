package com.app.drivedata.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.CollectionInterval
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.vehicle.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val username: String = "",
    val displayName: String = "",
    val email: String? = null,
    val vehicleCount: Int = 0,
    val adapterConnected: Boolean = false,
    val internetAvailable: Boolean = false,
    val collectionIntervalSeconds: Long = CollectionInterval.DEFAULT_SECONDS,
)

/**
 * Supplies the profile tab with the signed-in identity, garage size, live adapter/internet status
 * and the persisted collection interval.
 */
class ProfileViewModel(
    signedInUser: StateFlow<String?>,
    vehicles: Flow<List<Vehicle>>,
    connectionState: StateFlow<ObdConnectionState>,
    internetAvailable: Flow<Boolean>,
    collectionInterval: Flow<Long>,
    private val saveCollectionInterval: suspend (Long) -> Unit,
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

    private val connectivity: Flow<ProfileConnectivity> =
        combine(connectionState, internetAvailable) { connection, internet ->
            ProfileConnectivity(
                adapterConnected = connection is ObdConnectionState.Connected,
                internetAvailable = internet,
            )
        }

    val uiState: StateFlow<ProfileUiState> =
        combine(identity, vehicles, connectivity, collectionInterval) { id, allVehicles, connectionInfo, intervalSeconds ->
            ProfileUiState(
                username = id.username,
                displayName = id.displayName,
                email = id.email,
                vehicleCount = allVehicles.size,
                adapterConnected = connectionInfo.adapterConnected,
                internetAvailable = connectionInfo.internetAvailable,
                collectionIntervalSeconds = intervalSeconds,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ProfileUiState(),
        )

    /** Persists the chosen interval; the collector picks it up on its next collection cycle. */
    fun setCollectionIntervalSeconds(seconds: Long) {
        viewModelScope.launch {
            saveCollectionInterval(
                seconds.coerceIn(CollectionInterval.MIN_SECONDS, CollectionInterval.MAX_SECONDS),
            )
        }
    }

    private data class ProfileIdentity(
        val username: String,
        val displayName: String,
        val email: String?,
    )

    private data class ProfileConnectivity(
        val adapterConnected: Boolean,
        val internetAvailable: Boolean,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    ProfileViewModel(
                        signedInUser = container.authSession.signedInUser,
                        vehicles = container.vehicleRepository.observeVehicles(),
                        connectionState = container.obdConnectionManager.state,
                        internetAvailable = container.connectivityObserver.isAvailable,
                        collectionInterval = container.collectionIntervalSeconds,
                        saveCollectionInterval = container.settingsRepository::saveCollectionIntervalSeconds,
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
