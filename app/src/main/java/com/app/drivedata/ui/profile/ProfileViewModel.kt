package com.app.drivedata.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.ObdMetricsCollector
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.vehicle.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val username: String = "",
    val displayName: String = "",
    val email: String? = null,
    val vehicleCount: Int = 0,
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
class ProfileViewModel(
    signedInUser: StateFlow<String?>,
    vehicles: Flow<List<Vehicle>>,
    connectionState: StateFlow<ObdConnectionState>,
    internetAvailable: Flow<Boolean>,
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

    private val connectivity: Flow<ProfileConnectivity> =
        combine(connectionState, internetAvailable) { connection, internet ->
            ProfileConnectivity(
                adapterConnected = connection is ObdConnectionState.Connected,
                internetAvailable = internet,
            )
        }

    val uiState: StateFlow<ProfileUiState> =
        combine(identity, vehicles, connectivity) { id, allVehicles, connectionInfo ->
            ProfileUiState(
                username = id.username,
                displayName = id.displayName,
                email = id.email,
                vehicleCount = allVehicles.size,
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
