package com.app.drivedata.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.settings.AppSettingsRepository
import com.app.drivedata.vehicle.VehicleRepository
import com.app.drivedata.vehicle.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val host: String = "",
    val port: String = "",
    val connection: ObdConnectionState = ObdConnectionState.Disconnected,
    val isBusy: Boolean = false,
    val supportedPids: SupportedPidsSummary = SupportedPidsSummary(),
) {
    val isConfigValid: Boolean
        get() {
            val portNumber = port.toIntOrNull() ?: return false
            return host.isNotBlank() && portNumber in 1..65535
        }
}

class ConnectionViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val vehicleRepository: VehicleRepository,
    private val obdConnectionManager: ObdConnectionManager,
    activeVehicle: Flow<Vehicle?>,
) : ViewModel() {

    private val host = MutableStateFlow("")
    private val port = MutableStateFlow("")

    private val currentVehicle: StateFlow<Vehicle?> =
        activeVehicle.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val uiState: StateFlow<ConnectionUiState> =
        combine(host, port, obdConnectionManager.state, currentVehicle) { hostValue, portValue, connection, vehicle ->
            ConnectionUiState(
                host = hostValue,
                port = portValue,
                connection = connection,
                isBusy = connection is ObdConnectionState.Connecting ||
                    connection is ObdConnectionState.Initializing,
                supportedPids = vehicle.toSupportedPidsSummary(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ConnectionUiState(),
        )

    init {
        viewModelScope.launch {
            currentVehicle.collect { vehicle ->
                if (obdConnectionManager.state.value.isSessionActive) return@collect
                syncFieldsFrom(vehicle)
            }
        }
    }

    /**
     * Mirrors the selected vehicle's ELM adapter address into the editable IP/PORT fields.
     * Vehicles without a configured adapter leave the fields empty, prompting the user to pair.
     */
    private fun syncFieldsFrom(vehicle: Vehicle?) {
        val connection = vehicle?.connection
        if (connection != null && connection.isConfigured) {
            host.value = connection.host
            port.value = connection.port.toString()
        } else {
            host.value = ""
            port.value = ""
        }
    }

    fun onHostChange(value: String) {
        host.value = value.filter { it.isDigit() || it == '.' }.take(15)
    }

    fun onPortChange(value: String) {
        port.value = value.filter(Char::isDigit).take(5)
    }

    fun connect() {
        val config = ObdWifiConfig(host = host.value.trim(), port = port.value.toIntOrNull() ?: 0)
        viewModelScope.launch {
            if (config.isValid) {
                settingsRepository.saveWifiConfig(config)
                currentVehicle.value?.let { vehicle ->
                    vehicleRepository.saveConnectionConfig(vehicle.id, config.host, config.port)
                }
            }
            obdConnectionManager.connect(config)
        }
    }

    fun disconnect() {
        viewModelScope.launch { obdConnectionManager.disconnect() }
    }

    fun testCommunication() = connect()

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ConnectionViewModel(
                        settingsRepository = application.container.settingsRepository,
                        vehicleRepository = application.container.vehicleRepository,
                        obdConnectionManager = application.container.obdConnectionManager,
                        activeVehicle = application.container.vehicleRepository.observeActiveVehicle(),
                    )
                }
            }
    }
}
