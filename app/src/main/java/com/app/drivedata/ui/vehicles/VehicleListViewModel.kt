package com.app.drivedata.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.vehicle.ActiveVehicleSwitcher
import com.app.drivedata.vehicle.VehicleRepository
import com.app.drivedata.vehicle.model.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VehicleListUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    val error: VehicleListError? = null,
)

enum class VehicleListError {
    ACTIVATE_FAILED,
    DELETE_FAILED,
}

class VehicleListViewModel(
    private val vehicleRepository: VehicleRepository,
    private val vehicleSwitcher: ActiveVehicleSwitcher,
    private val connectionManager: ObdConnectionManager,
) : ViewModel() {

    private val error = MutableStateFlow<VehicleListError?>(null)

    val uiState: StateFlow<VehicleListUiState> =
        combine(vehicleRepository.observeVehicles(), error) { vehicles, currentError ->
            VehicleListUiState(
                vehicles = vehicles,
                isLoading = false,
                error = currentError,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = VehicleListUiState(),
        )

    fun selectActive(vehicleId: String) {
        viewModelScope.launch {
            val target = uiState.value.vehicles.firstOrNull { it.id == vehicleId } ?: return@launch
            if (vehicleSwitcher.switchTo(target)) {
                error.value = null
            } else {
                error.value = VehicleListError.ACTIVATE_FAILED
            }
        }
    }

    fun delete(vehicle: Vehicle) {
        viewModelScope.launch {
            if (isConnectedTo(vehicle)) {
                connectionManager.disconnect()
            }
            if (vehicleRepository.delete(vehicle.id)) {
                error.value = null
            } else {
                error.value = VehicleListError.DELETE_FAILED
            }
        }
    }

    fun dismissError() {
        error.value = null
    }

    private fun isConnectedTo(vehicle: Vehicle): Boolean {
        val connection = vehicle.connection
        if (!connection.isConfigured) return false
        val live = connectionManager.state.value.configOrNull() ?: return false
        return live.host == connection.host && live.port == connection.port
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    VehicleListViewModel(
                        vehicleRepository = container.vehicleRepository,
                        vehicleSwitcher = container.activeVehicleSwitcher,
                        connectionManager = container.obdConnectionManager,
                    )
                }
            }
    }
}

private fun ObdConnectionState.configOrNull(): ObdWifiConfig? =
    when (this) {
        is ObdConnectionState.Connected -> config
        is ObdConnectionState.Initializing -> config
        is ObdConnectionState.Connecting -> config
        is ObdConnectionState.Failed -> config
        is ObdConnectionState.Disconnected -> null
    }
