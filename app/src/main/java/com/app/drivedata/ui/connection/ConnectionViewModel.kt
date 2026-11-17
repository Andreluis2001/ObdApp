package com.app.drivedata.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.MetricsCollectionController
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.DiscoveryFailure
import com.app.drivedata.obd.model.DiscoveryStage
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.obd.model.PidDiscoveryResult
import com.app.drivedata.settings.AppSettingsRepository
import com.app.drivedata.ui.discovery.SetupFailure
import com.app.drivedata.ui.discovery.SetupStage
import com.app.drivedata.ui.discovery.setupStage
import com.app.drivedata.ui.discovery.toSetupFailure
import com.app.drivedata.vehicle.ConnectionConfigValidation
import com.app.drivedata.vehicle.ConnectionConfigValidator
import com.app.drivedata.vehicle.VehicleRepository
import com.app.drivedata.vehicle.model.ConnectionStatus
import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleDetails
import com.app.drivedata.vehicle.model.VehicleIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    val discovery: VehicleDiscovery = VehicleDiscovery.Idle,
    val supportedPids: SupportedPidsSummary = SupportedPidsSummary(),
) {
    val isConfigValid: Boolean
        get() {
            val portNumber = port.toIntOrNull() ?: return false
            return host.isNotBlank() && portNumber in 1..65535
        }

    val isDiscovering: Boolean
        get() = discovery is VehicleDiscovery.Running

    val isBusy: Boolean
        get() =
            isDiscovering ||
                connection is ObdConnectionState.Connecting ||
                connection is ObdConnectionState.Initializing
}

/** Progress and outcome of the VIN-based vehicle registration triggered from the connection tab. */
sealed interface VehicleDiscovery {
    data object Idle : VehicleDiscovery

    data class Running(
        val stage: SetupStage,
        val rangeBase: Int = 0x00,
        val foundCount: Int = 0,
    ) : VehicleDiscovery

    data class Succeeded(
        val vehicleId: String,
        val vehicleName: String,
        val vin: String,
        val manufacturer: String?,
        val serialNumber: String?,
        val alreadyRegistered: Boolean,
    ) : VehicleDiscovery

    data class Failed(val stage: SetupStage, val failure: SetupFailure) : VehicleDiscovery
}

/**
 * Connects to the ELM327 and registers the vehicle from the VIN reported by the ECU.
 *
 * Discovery is the only way a vehicle is created: it reads the VIN, walks the supported PIDs and
 * then either creates the vehicle or updates the existing one with the same serial number.
 */
class ConnectionViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val vehicleRepository: VehicleRepository,
    private val obdConnectionManager: ObdConnectionManager,
    private val discoverPids:
        suspend (onProgress: (rangeBase: Int, foundCount: Int) -> Unit) -> PidDiscoveryResult,
    private val readVehicleIdentity: suspend () -> VehicleIdentity?,
    private val metricsController: MetricsCollectionController,
    activeVehicle: Flow<Vehicle?>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val host = MutableStateFlow("")
    private val port = MutableStateFlow("")
    private val discovery = MutableStateFlow<VehicleDiscovery>(VehicleDiscovery.Idle)

    private var discoveryJob: Job? = null

    private val currentVehicle: StateFlow<Vehicle?> =
        activeVehicle.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val uiState: StateFlow<ConnectionUiState> =
        combine(
            host,
            port,
            obdConnectionManager.state,
            currentVehicle,
            discovery,
        ) { hostValue, portValue, connection, vehicle, discoveryState ->
            ConnectionUiState(
                host = hostValue,
                port = portValue,
                connection = connection,
                discovery = discoveryState,
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
        if (discovery.value is VehicleDiscovery.Running) return
        host.value = value.filter { it.isDigit() || it == '.' }.take(15)
        clearResult()
    }

    fun onPortChange(value: String) {
        if (discovery.value is VehicleDiscovery.Running) return
        port.value = value.filter(Char::isDigit).take(5)
        clearResult()
    }

    /**
     * Connects to the adapter, reads the VIN, discovers the supported PIDs and registers the
     * vehicle. An already registered serial number is treated as a re-pair instead of a duplicate.
     */
    fun discoverVehicle() {
        if (uiState.value.isBusy) return
        val config =
            when (val validation = ConnectionConfigValidator.validate(host.value, port.value)) {
                is ConnectionConfigValidation.Valid -> validation.config
                ConnectionConfigValidation.Unconfigured,
                is ConnectionConfigValidation.Invalid,
                -> {
                    discovery.value =
                        VehicleDiscovery.Failed(
                            SetupStage.CONNECT,
                            SetupFailure.INVALID_CONFIGURATION,
                        )
                    return
                }
            }

        discoveryJob?.cancel()
        discoveryJob =
            viewModelScope.launch {
                metricsController.pauseForMaintenance()
                try {
                    settingsRepository.saveWifiConfig(config)
                    discovery.value = VehicleDiscovery.Running(SetupStage.CONNECT)

                    val connected = connect(config) ?: return@launch
                    if (connected.ecuStatus != EcuStatus.VERIFIED) {
                        discovery.value =
                            VehicleDiscovery.Failed(
                                stage = SetupStage.ECU_TEST,
                                failure =
                                    if (connected.ecuStatus == EcuStatus.INVALID_RESPONSE) {
                                        SetupFailure.MALFORMED_RESPONSE
                                    } else {
                                        SetupFailure.ECU_NO_DATA
                                    },
                            )
                        disconnectLink()
                        return@launch
                    }

                    discovery.value = VehicleDiscovery.Running(SetupStage.IDENTITY)
                    val identity = readIdentitySafely()
                    if (identity == null) {
                        discovery.value =
                            VehicleDiscovery.Failed(
                                SetupStage.IDENTITY,
                                SetupFailure.VIN_UNAVAILABLE,
                            )
                        disconnectLink()
                        return@launch
                    }

                    discovery.value = VehicleDiscovery.Running(SetupStage.DISCOVERY)
                    val result = runDiscovery()
                    if (result is PidDiscoveryResult.Failure) {
                        discovery.value =
                            VehicleDiscovery.Failed(
                                SetupStage.DISCOVERY,
                                result.reason.toSetupFailure(),
                            )
                        disconnectLink()
                        return@launch
                    }

                    discovery.value = VehicleDiscovery.Running(SetupStage.SAVE)
                    registerVehicle(
                        identity = identity,
                        config = config,
                        discovery = result as PidDiscoveryResult.Success,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    discovery.value = VehicleDiscovery.Failed(SetupStage.CONNECT, SetupFailure.UNKNOWN)
                } finally {
                    metricsController.resumeAfterMaintenance()
                }
            }
    }

    /** Cancels a running discovery and drops the adapter link it opened. */
    fun cancelDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        discovery.value = VehicleDiscovery.Idle
        viewModelScope.launch { obdConnectionManager.disconnect() }
    }

    fun disconnect() {
        viewModelScope.launch { obdConnectionManager.disconnect() }
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        discoveryJob = null
        super.onCleared()
    }

    /**
     * Opens the adapter and follows the manager's state so the UI can show the connection and
     * initialization steps. Returns the connected state, or records the failure and returns null.
     */
    private suspend fun connect(config: ObdWifiConfig): ObdConnectionState.Connected? =
        coroutineScope {
            val monitor =
                launch {
                    obdConnectionManager.state.collect { state ->
                        when (state) {
                            is ObdConnectionState.Connecting ->
                                discovery.value = VehicleDiscovery.Running(SetupStage.CONNECT)

                            is ObdConnectionState.Initializing ->
                                discovery.value = VehicleDiscovery.Running(SetupStage.INITIALIZE)

                            else -> Unit
                        }
                    }
                }
            try {
                obdConnectionManager.connect(config)
            } finally {
                monitor.cancel()
            }

            val connected = obdConnectionManager.state.value as? ObdConnectionState.Connected
            if (connected == null) {
                val failure =
                    (obdConnectionManager.state.value as? ObdConnectionState.Failed)?.reason
                discovery.value =
                    VehicleDiscovery.Failed(
                        stage = failure.setupStage(),
                        failure = failure.toSetupFailure(),
                    )
            }
            connected
        }

    private suspend fun runDiscovery(): PidDiscoveryResult =
        try {
            discoverPids { base, found ->
                discovery.value = VehicleDiscovery.Running(SetupStage.DISCOVERY, base, found)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PidDiscoveryResult.Failure(DiscoveryStage.RANGE_QUERY, DiscoveryFailure.UNKNOWN)
        }

    private suspend fun readIdentitySafely(): VehicleIdentity? =
        try {
            readVehicleIdentity()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }

    /** Drops a link opened by a discovery that could not register a vehicle. */
    private suspend fun disconnectLink() {
        obdConnectionManager.disconnect()
    }

    /**
     * Creates the vehicle or updates the existing one found by serial number (falling back to the
     * VIN). The discovered vehicle always becomes active so collection follows the live link.
     */
    private suspend fun registerVehicle(
        identity: VehicleIdentity,
        config: ObdWifiConfig,
        discovery: PidDiscoveryResult.Success,
    ) {
        val identityKey = identity.serialNumber ?: identity.vin
        val now = clock()
        // A previous discovery may have stored the VIN without a decoded serial number, so the
        // lookup also tries the VIN when this one decoded a serial.
        val existing =
            vehicleRepository.findByIdentityKey(identityKey)
                ?: identity.vin
                    .takeIf { it != identityKey }
                    ?.let { vehicleRepository.findByIdentityKey(it) }
        val vehicleId =
            if (existing != null) {
                val saved =
                    vehicleRepository.saveConnectionConfig(existing.id, config.host, config.port) &&
                        vehicleRepository.recordAdapterConnected(existing.id, now) &&
                        vehicleRepository.recordEcuVerified(existing.id, now) &&
                        vehicleRepository.replaceCapabilities(
                            vehicleId = existing.id,
                            pids = discovery.pids,
                            discoveredAtMillis = discovery.discoveredAtMillis,
                        )
                if (saved && vehicleRepository.setActive(existing.id)) existing.id else null
            } else {
                val created =
                    vehicleRepository.create(
                        details =
                            VehicleDetails(
                                make = identity.manufacturer.orEmpty(),
                                model = "",
                            ),
                        identity = identity,
                        connection =
                            Elm327Connection(
                                host = config.host,
                                port = config.port,
                                lastConnectionAtMillis = now,
                                lastEcuCommunicationAtMillis = now,
                                status = ConnectionStatus.CONNECTED,
                            ),
                        discoveredPids = discovery.pids,
                        discoveredAtMillis = discovery.discoveredAtMillis,
                    )
                if (created != null && vehicleRepository.setActive(created.id)) {
                    created.id
                } else {
                    created?.id
                }
            }

        if (vehicleId == null) {
            this.discovery.value =
                VehicleDiscovery.Failed(SetupStage.SAVE, SetupFailure.SAVE_FAILED)
            disconnectLink()
            return
        }
        this.discovery.value =
            VehicleDiscovery.Succeeded(
                vehicleId = vehicleId,
                vehicleName = identity.displayName,
                vin = identity.vin,
                manufacturer = identity.manufacturer,
                serialNumber = identity.serialNumber,
                alreadyRegistered = existing != null,
            )
    }

    private fun clearResult() {
        if (discovery.value !is VehicleDiscovery.Running) {
            discovery.value = VehicleDiscovery.Idle
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    ConnectionViewModel(
                        settingsRepository = container.settingsRepository,
                        vehicleRepository = container.vehicleRepository,
                        obdConnectionManager = container.obdConnectionManager,
                        discoverPids = { onProgress ->
                            container.pidDiscoveryCoordinator.discover(onProgress)
                        },
                        readVehicleIdentity = container.vehicleIdentityExtractor::read,
                        metricsController = container.metricsCollectionController,
                        activeVehicle = container.vehicleRepository.observeActiveVehicle(),
                    )
                }
            }
    }
}
