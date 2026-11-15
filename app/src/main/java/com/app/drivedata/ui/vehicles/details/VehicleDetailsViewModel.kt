package com.app.drivedata.ui.vehicles.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.MetricsCollectionController
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.obd.model.PidDiscoveryResult
import com.app.drivedata.ui.discovery.SetupFailure
import com.app.drivedata.ui.discovery.SetupStage
import com.app.drivedata.ui.discovery.setupStage
import com.app.drivedata.ui.discovery.toSetupFailure
import com.app.drivedata.vehicle.VehicleRepository
import com.app.drivedata.vehicle.model.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Drives the vehicle details screen: capability review, rediscovery, activation and removal.
 *
 * Rediscovery connects with the vehicle's stored adapter settings. A failed run never replaces the
 * stored capability rows, so the previous PIDs and timestamp stay visible.
 */
class VehicleDetailsViewModel(
    private val vehicleId: String,
    private val vehicleRepository: VehicleRepository,
    private val connectionManager: ObdConnectionManager,
    private val discoverPids:
        suspend (onProgress: (rangeBase: Int, foundCount: Int) -> Unit) -> PidDiscoveryResult,
    private val metricsController: MetricsCollectionController,
) : ViewModel() {

    private val _state = MutableStateFlow(VehicleDetailsUiState())
    val state: StateFlow<VehicleDetailsUiState> = _state.asStateFlow()

    private var rediscoveryJob: Job? = null

    init {
        viewModelScope.launch {
            vehicleRepository.observeVehicle(vehicleId).collect { vehicle ->
                _state.value = _state.value.copy(vehicle = vehicle, isLoading = false)
            }
        }
    }

    fun rediscover() {
        if (_state.value.isBusy) return
        val vehicle = _state.value.vehicle ?: return
        val connection = vehicle.connection
        if (!connection.isConfigured) {
            _state.value = _state.value.copy(error = DetailsError.NotConfigured)
            return
        }

        rediscoveryJob =
            viewModelScope.launch {
                metricsController.pauseForMaintenance()
                _state.value =
                    _state.value.copy(operation = DetailsOperation.Connecting, error = null)
                try {
                    connectionManager.connect(ObdWifiConfig(connection.host, connection.port))
                    val connected = connectionManager.state.value
                    if (connected !is ObdConnectionState.Connected) {
                        vehicleRepository.markError(vehicleId)
                        val failure = (connected as? ObdConnectionState.Failed)?.reason
                        _state.value =
                            _state.value.copy(
                                operation = DetailsOperation.Idle,
                                error =
                                    DetailsError.Rediscovery(
                                        failure.setupStage(),
                                        failure.toSetupFailure(),
                                    ),
                            )
                        return@launch
                    }

                    if (connected.ecuStatus != EcuStatus.VERIFIED) {
                        vehicleRepository.markError(vehicleId)
                        _state.value =
                            _state.value.copy(
                                operation = DetailsOperation.Idle,
                                error =
                                    DetailsError.Rediscovery(
                                        stage = SetupStage.ECU_TEST,
                                        failure =
                                            if (connected.ecuStatus == EcuStatus.INVALID_RESPONSE) {
                                                SetupFailure.MALFORMED_RESPONSE
                                            } else {
                                                SetupFailure.ECU_NO_DATA
                                            },
                                    ),
                            )
                        return@launch
                    }

                    vehicleRepository.recordAdapterConnected(vehicleId)
                    vehicleRepository.recordEcuVerified(vehicleId)
                    _state.value =
                        _state.value.copy(
                            operation = DetailsOperation.Discovering(0x00, 0),
                        )
                    val result =
                        discoverPids { base, found ->
                            _state.value =
                                _state.value.copy(
                                    operation = DetailsOperation.Discovering(base, found),
                                )
                        }
                    when (result) {
                        is PidDiscoveryResult.Success -> {
                            vehicleRepository.replaceCapabilities(
                                vehicleId = vehicleId,
                                pids = result.pids,
                                discoveredAtMillis = result.discoveredAtMillis,
                            )
                            _state.value =
                                _state.value.copy(operation = DetailsOperation.Idle, error = null)
                        }

                        is PidDiscoveryResult.Failure ->
                            _state.value =
                                _state.value.copy(
                                    operation = DetailsOperation.Idle,
                                    error =
                                        DetailsError.Rediscovery(
                                            SetupStage.DISCOVERY,
                                            result.reason.toSetupFailure(),
                                        ),
                                )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _state.value =
                        _state.value.copy(
                            operation = DetailsOperation.Idle,
                            error = DetailsError.Rediscovery(SetupStage.CONNECT, SetupFailure.UNKNOWN),
                        )
                } finally {
                    metricsController.resumeAfterMaintenance()
                }
            }
    }

    fun setActive() {
        viewModelScope.launch {
            val vehicle = _state.value.vehicle
            if (vehicle != null &&
                connectionManager.state.value !is ObdConnectionState.Disconnected &&
                !isConnectedTo(vehicle)
            ) {
                connectionManager.disconnect()
            }
            if (vehicleRepository.setActive(vehicleId)) {
                _state.value = _state.value.copy(error = null)
            } else {
                _state.value = _state.value.copy(error = DetailsError.ActivateFailed)
            }
        }
    }

    fun setPidEnabled(pid: Int, enabled: Boolean) {
        viewModelScope.launch {
            if (vehicleRepository.setPidEnabled(vehicleId, pid, enabled)) {
                _state.value = _state.value.copy(error = null)
            } else {
                _state.value = _state.value.copy(error = DetailsError.PidUpdateFailed)
            }
        }
    }

    fun delete() {
        val vehicle = _state.value.vehicle ?: return
        viewModelScope.launch {
            if (isConnectedTo(vehicle)) {
                connectionManager.disconnect()
            }
            if (vehicleRepository.delete(vehicleId)) {
                _state.value = _state.value.copy(removed = true, error = null)
            } else {
                _state.value = _state.value.copy(error = DetailsError.DeleteFailed)
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Called when the details route leaves the screen so a rediscovery does not run unseen. */
    fun onScreenDisposed() {
        rediscoveryJob?.cancel()
        rediscoveryJob = null
    }

    override fun onCleared() {
        rediscoveryJob?.cancel()
        rediscoveryJob = null
        super.onCleared()
    }

    private fun isConnectedTo(vehicle: Vehicle): Boolean {
        val connection = vehicle.connection
        if (!connection.isConfigured) return false
        val live = connectionManager.state.value.configOrNull() ?: return false
        return live.host == connection.host && live.port == connection.port
    }

    companion object {
        fun factory(
            application: DriveDataApplication,
            vehicleId: String,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    VehicleDetailsViewModel(
                        vehicleId = vehicleId,
                        vehicleRepository = container.vehicleRepository,
                        connectionManager = container.obdConnectionManager,
                        discoverPids = { onProgress ->
                            container.pidDiscoveryCoordinator.discover(onProgress)
                        },
                        metricsController = container.metricsCollectionController,
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
