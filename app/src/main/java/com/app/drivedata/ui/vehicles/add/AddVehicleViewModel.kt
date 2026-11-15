package com.app.drivedata.ui.vehicles.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.MetricDefinition
import com.app.drivedata.metrics.MetricsCollectionController
import com.app.drivedata.metrics.defaultMetricCatalog
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.DiscoveryFailure
import com.app.drivedata.obd.model.DiscoveryStage
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.obd.model.PidDiscoveryResult
import com.app.drivedata.settings.AppSettingsRepository
import com.app.drivedata.ui.vehicles.AddVehicleEvent
import com.app.drivedata.ui.vehicles.DiscoveredPid
import com.app.drivedata.ui.vehicles.FormErrors
import com.app.drivedata.ui.vehicles.FormStep
import com.app.drivedata.ui.vehicles.SetupFailure
import com.app.drivedata.ui.vehicles.SetupStage
import com.app.drivedata.ui.vehicles.VehicleSetupState
import com.app.drivedata.ui.vehicles.setupStage
import com.app.drivedata.ui.vehicles.toSetupFailure
import com.app.drivedata.vehicle.ConnectionConfigErrors
import com.app.drivedata.vehicle.ConnectionConfigValidation
import com.app.drivedata.vehicle.ConnectionConfigValidator
import com.app.drivedata.vehicle.ConnectionDraft
import com.app.drivedata.vehicle.VehicleDetailsDraft
import com.app.drivedata.vehicle.VehicleDetailsErrors
import com.app.drivedata.vehicle.VehicleDetailsValidation
import com.app.drivedata.vehicle.VehicleDetailsValidator
import com.app.drivedata.vehicle.VehicleRepository
import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.VehicleDetails
import com.app.drivedata.vehicle.model.VehicleIdentity
import com.app.drivedata.vehicle.resolveConnectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Drives the add-vehicle setup: vehicle form, adapter form, connection/initialization/ECU test,
 * PID discovery, review and save.
 *
 * A vehicle can be saved after a failed setup or without testing. In that case the adapter
 * configuration is stored with `ERROR`/`DISCONNECTED` (or no connection row for `NOT_CONFIGURED`)
 * and no capabilities are invented.
 */
class AddVehicleViewModel(
    private val vehicleRepository: VehicleRepository,
    private val settingsRepository: AppSettingsRepository,
    private val connectionManager: ObdConnectionManager,
    private val discoverPids: suspend (onProgress: (rangeBase: Int, foundCount: Int) -> Unit) -> PidDiscoveryResult,
    private val readVehicleIdentity: suspend () -> VehicleIdentity?,
    private val metricsController: MetricsCollectionController,
    private val catalog: List<MetricDefinition> = defaultMetricCatalog,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow<VehicleSetupState>(VehicleSetupState.Form())
    val state: StateFlow<VehicleSetupState> = _state.asStateFlow()

    private val _event = MutableStateFlow<AddVehicleEvent?>(null)
    val event: StateFlow<AddVehicleEvent?> = _event.asStateFlow()

    private var lastForm: VehicleSetupState.Form = VehicleSetupState.Form()
    private var setupJob: Job? = null
    private var validatedDetails: VehicleDetails? = null
    private var lastReview: VehicleSetupState.Review? = null

    private var attemptedConnection = false
    private var connectedAtMillis: Long? = null
    private var ecuVerifiedAtMillis: Long? = null
    private var vehicleIdentity: VehicleIdentity? = null
    private var saved = false

    private val connectionAtEntry: ObdConnectionState = connectionManager.state.value

    init {
        viewModelScope.launch {
            val config = settingsRepository.currentWifiConfig()
            val current = _state.value as? VehicleSetupState.Form ?: return@launch
            if (current.connection.host.isNotBlank() || current.connection.port.isNotBlank()) {
                return@launch
            }
            val updated =
                current.copy(
                    connection = ConnectionDraft(host = config.host, port = config.port.toString()),
                )
            lastForm = updated
            _state.value = updated
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    fun onMakeChange(value: String) {
        updateForm { form ->
            form.copy(
                details = form.details.copy(make = value),
                errors = form.errors.copy(details = form.errors.details.copy(make = false)),
            )
        }
    }

    fun onModelChange(value: String) {
        updateForm { form ->
            form.copy(
                details = form.details.copy(model = value),
                errors = form.errors.copy(details = form.errors.details.copy(model = false)),
            )
        }
    }

    fun onPlateChange(value: String) {
        val normalized = value.uppercase().filter(Char::isLetterOrDigit).take(7)
        updateForm { form ->
            form.copy(
                details = form.details.copy(licensePlate = normalized),
                errors = form.errors.copy(details = form.errors.details.copy(licensePlate = false)),
            )
        }
    }

    fun onHostChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }.take(15)
        resetAttemptState()
        lastReview = null
        updateForm { form ->
            form.copy(
                connection = form.connection.copy(host = filtered),
                errors = form.errors.copy(connection = form.errors.connection.copy(host = false)),
            )
        }
    }

    fun onPortChange(value: String) {
        val digits = value.filter(Char::isDigit).take(5)
        resetAttemptState()
        lastReview = null
        updateForm { form ->
            form.copy(
                connection = form.connection.copy(port = digits),
                errors = form.errors.copy(connection = form.errors.connection.copy(port = false)),
            )
        }
    }

    fun onContinue() {
        val form = currentForm() ?: return
        when (val result = VehicleDetailsValidator.validate(form.details)) {
            is VehicleDetailsValidation.Valid -> {
                validatedDetails = result.details
                setForm(
                    form.copy(
                        step = FormStep.ADAPTER,
                        errors = form.errors.copy(details = VehicleDetailsErrors()),
                    ),
                )
            }

            is VehicleDetailsValidation.Invalid ->
                setForm(form.copy(errors = form.errors.copy(details = result.errors)))
        }
    }

    fun onBackToVehicleStep() {
        updateForm { form ->
            form.copy(
                step = FormStep.VEHICLE,
                errors = form.errors.copy(connection = ConnectionConfigErrors()),
            )
        }
    }

    fun onTestConnection() {
        val form = currentForm() ?: return
        val details = validateDetails(form) ?: return
        val config =
            when (val validation = validateConnection(form, requireConfigured = true)) {
                is ConnectionConfigValidation.Valid -> validation.config
                ConnectionConfigValidation.Unconfigured,
                is ConnectionConfigValidation.Invalid,
                -> return
            }
        validatedDetails = details
        lastForm = form.copy(step = FormStep.ADAPTER)
        startSetup(details, config)
    }

    fun onSaveWithoutTesting() {
        val form = currentForm() ?: return
        val details = validateDetails(form) ?: return
        val config =
            when (val validation = validateConnection(form, requireConfigured = false)) {
                is ConnectionConfigValidation.Valid -> validation.config
                ConnectionConfigValidation.Unconfigured -> null
                is ConnectionConfigValidation.Invalid -> return
            }
        validatedDetails = details
        lastForm = form.copy(step = FormStep.ADAPTER)
        lastReview = null
        resetAttemptState()
        _state.value = VehicleSetupState.Saving
        viewModelScope.launch {
            if (config != null) {
                runCatching { settingsRepository.saveWifiConfig(config) }
            }
            performSave(details, config, attempted = false, review = null)
        }
    }

    fun onSave() {
        val form = lastForm
        val details = validatedDetails ?: validateDetails(form) ?: return
        val config =
            when (val validation = validateConnection(form, requireConfigured = false)) {
                is ConnectionConfigValidation.Valid -> validation.config
                ConnectionConfigValidation.Unconfigured -> null
                is ConnectionConfigValidation.Invalid -> return
            }
        val review = (_state.value as? VehicleSetupState.Review) ?: lastReview
        _state.value = VehicleSetupState.Saving
        viewModelScope.launch {
            performSave(details, config, attemptedConnection, review)
        }
    }

    fun onPidToggle(pid: Int, enabled: Boolean) {
        val review = _state.value as? VehicleSetupState.Review ?: return
        val updated =
            review.copy(
                pids = review.pids.map { if (it.pid == pid) it.copy(enabled = enabled) else it },
            )
        lastReview = updated
        _state.value = updated
    }

    fun onRediscover() {
        if (_state.value !is VehicleSetupState.Review) return
        setupJob?.cancel()
        setupJob =
            viewModelScope.launch {
                val connected = connectionManager.state.value as? ObdConnectionState.Connected
                if (connected == null || connected.ecuStatus != EcuStatus.VERIFIED) {
                    _state.value =
                        VehicleSetupState.Failed(SetupStage.DISCOVERY, SetupFailure.CONNECTION_LOST)
                    return@launch
                }
                runDiscovery()
            }
    }

    fun onRetry() {
        val current = _state.value as? VehicleSetupState.Failed ?: return
        if (current.stage == SetupStage.SAVE) {
            onSave()
        } else {
            retrySetup()
        }
    }

    fun onBack() {
        when (val current = _state.value) {
            is VehicleSetupState.Form ->
                if (current.step == FormStep.ADAPTER) onBackToVehicleStep() else cancelFlow()

            is VehicleSetupState.Review -> openAdapterStep()

            is VehicleSetupState.Failed ->
                when {
                    current.stage == SetupStage.SAVE && lastReview != null ->
                        _state.value = lastReview!!

                    current.stage == SetupStage.DISCOVERY && lastReview != null ->
                        _state.value = lastReview!!

                    else -> openAdapterStep()
                }

            VehicleSetupState.Saving -> Unit

            else -> cancelSetupAndShowAdapter()
        }
    }

    fun cancelFlow() {
        if (saved) return
        setupJob?.cancel()
        setupJob = null
        viewModelScope.launch {
            disconnectIfNeeded()
            metricsController.resumeAfterMaintenance()
            _event.value = AddVehicleEvent.Cancelled
        }
    }

    /** Called by the screen when it leaves the composition (tab change, route change, etc.). */
    fun onScreenDisposed() {
        if (saved || _state.value is VehicleSetupState.Saving) return
        setupJob?.cancel()
        setupJob = null
        viewModelScope.launch {
            disconnectIfNeeded()
            metricsController.resumeAfterMaintenance()
        }
    }

    override fun onCleared() {
        setupJob?.cancel()
        setupJob = null
        metricsController.resumeAfterMaintenance()
        super.onCleared()
    }

    private fun startSetup(details: VehicleDetails, config: ObdWifiConfig) {
        setupJob?.cancel()
        resetAttemptState()
        attemptedConnection = true
        metricsController.pauseForMaintenance()
        _state.value = VehicleSetupState.ConnectingAdapter
        setupJob =
            viewModelScope.launch {
                val monitor =
                    launch {
                        connectionManager.state.collect { state ->
                            when (state) {
                                is ObdConnectionState.Connecting ->
                                    _state.value = VehicleSetupState.ConnectingAdapter

                                is ObdConnectionState.Initializing ->
                                    _state.value = VehicleSetupState.InitializingAdapter

                                else -> Unit
                            }
                        }
                    }
                try {
                    settingsRepository.saveWifiConfig(config)
                    connectionManager.connect(config)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _state.value =
                        VehicleSetupState.Failed(SetupStage.CONNECT, SetupFailure.UNKNOWN)
                    return@launch
                } finally {
                    monitor.cancel()
                }

                val connected = connectionManager.state.value as? ObdConnectionState.Connected
                if (connected == null) {
                    val failure =
                        (connectionManager.state.value as? ObdConnectionState.Failed)?.reason
                    _state.value =
                        VehicleSetupState.Failed(
                            stage = failure.setupStage(),
                            failure = failure.toSetupFailure(),
                        )
                    return@launch
                }

                connectedAtMillis = clock()
                if (connected.ecuStatus == EcuStatus.VERIFIED) {
                    ecuVerifiedAtMillis = clock()
                    _state.value = VehicleSetupState.ReadingIdentity
                    vehicleIdentity = readIdentitySafely()
                    runDiscovery()
                } else {
                    _state.value =
                        VehicleSetupState.Failed(
                            stage = SetupStage.ECU_TEST,
                            failure =
                                if (connected.ecuStatus == EcuStatus.INVALID_RESPONSE) {
                                    SetupFailure.MALFORMED_RESPONSE
                                } else {
                                    SetupFailure.ECU_NO_DATA
                                },
                        )
                }
            }
    }

    private suspend fun runDiscovery() {
        _state.value = VehicleSetupState.TestingEcu
        _state.value = VehicleSetupState.DiscoveringPids(rangeBase = 0x00, foundCount = 0)
        val previousEnabled = lastReview?.pids?.associate { it.pid to it.enabled }.orEmpty()
        val result =
            try {
                discoverPids { base, found ->
                    _state.value = VehicleSetupState.DiscoveringPids(base, found)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                PidDiscoveryResult.Failure(DiscoveryStage.RANGE_QUERY, DiscoveryFailure.UNKNOWN)
            }
        when (result) {
            is PidDiscoveryResult.Success -> {
                val review =
                    buildReview(
                        pids = result.pids,
                        discoveredAtMillis = result.discoveredAtMillis,
                        previousEnabled = previousEnabled,
                        identity = vehicleIdentity,
                    )
                lastReview = review
                _state.value = review
            }

            is PidDiscoveryResult.Failure ->
                _state.value =
                    VehicleSetupState.Failed(
                        SetupStage.DISCOVERY,
                        result.reason.toSetupFailure(),
                    )
        }
    }

    private fun buildReview(
        pids: Set<Int>,
        discoveredAtMillis: Long,
        previousEnabled: Map<Int, Boolean>,
        identity: VehicleIdentity?,
    ): VehicleSetupState.Review {
        val reviewPids =
            catalog.mapNotNull { definition ->
                if (definition.pid !in pids) return@mapNotNull null
                DiscoveredPid(
                    pid = definition.pid,
                    metricId = definition.id,
                    enabled = previousEnabled[definition.pid] ?: true,
                )
            }
        return VehicleSetupState.Review(
            pids = reviewPids,
            discoveredPids = pids,
            discoveredAtMillis = discoveredAtMillis,
            identity = identity,
        )
    }

    private suspend fun performSave(
        details: VehicleDetails,
        wifiConfig: ObdWifiConfig?,
        attempted: Boolean,
        review: VehicleSetupState.Review?,
    ) {
        val currentlyConnected =
            attempted && connectionManager.state.value is ObdConnectionState.Connected
        val status =
            resolveConnectionStatus(
                configured = wifiConfig != null,
                attempted = attempted,
                currentlyConnected = currentlyConnected,
            )
        val now = clock()
        val connection =
            wifiConfig?.let {
                Elm327Connection(
                    host = it.host,
                    port = it.port,
                    lastConnectionAtMillis = connectedAtMillis,
                    lastEcuCommunicationAtMillis = ecuVerifiedAtMillis,
                    status = status,
                )
            }
        val vehicle =
            try {
                vehicleRepository.create(
                    details = details,
                    identity = vehicleIdentity,
                    connection = connection,
                    discoveredPids = review?.discoveredPids,
                    discoveredAtMillis = review?.discoveredAtMillis ?: now,
                    disabledPids =
                        review?.pids
                            ?.filterNot { it.enabled }
                            ?.map { it.pid }
                            ?.toSet()
                            .orEmpty(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            }
        if (vehicle == null) {
            metricsController.resumeAfterMaintenance()
            _state.value = VehicleSetupState.Failed(SetupStage.SAVE, SetupFailure.SAVE_FAILED)
            return
        }
        saved = true
        metricsController.resumeAfterMaintenance()
        _event.value = AddVehicleEvent.Saved(vehicle.id)
    }

    private fun retrySetup() {
        val form = lastForm
        val details = validatedDetails ?: validateDetails(form) ?: return
        validatedDetails = details
        val config =
            when (val validation = validateConnection(form, requireConfigured = true)) {
                is ConnectionConfigValidation.Valid -> validation.config
                ConnectionConfigValidation.Unconfigured,
                is ConnectionConfigValidation.Invalid,
                -> return
            }
        startSetup(details, config)
    }

    private fun validateDetails(form: VehicleSetupState.Form): VehicleDetails? =
        when (val result = VehicleDetailsValidator.validate(form.details)) {
            is VehicleDetailsValidation.Valid -> result.details

            is VehicleDetailsValidation.Invalid -> {
                setForm(form.copy(errors = form.errors.copy(details = result.errors)))
                null
            }
        }

    private fun validateConnection(
        form: VehicleSetupState.Form,
        requireConfigured: Boolean,
    ): ConnectionConfigValidation {
        val validation =
            ConnectionConfigValidator.validate(form.connection.host, form.connection.port)
        return when (validation) {
            is ConnectionConfigValidation.Invalid -> {
                setForm(
                    form.copy(
                        step = FormStep.ADAPTER,
                        errors = form.errors.copy(connection = validation.errors),
                    ),
                )
                validation
            }

            ConnectionConfigValidation.Unconfigured -> {
                if (requireConfigured) {
                    setForm(
                        form.copy(
                            step = FormStep.ADAPTER,
                            errors =
                                form.errors.copy(
                                    connection = ConnectionConfigErrors(host = true, port = true),
                                ),
                        ),
                    )
                }
                validation
            }

            is ConnectionConfigValidation.Valid -> validation
        }
    }

    /**
     * Closes the adapter link opened by this flow. A link that existed on entry and still targets
     * the same adapter is kept, so cancelling the form does not tear down a working connection.
     */
    private suspend fun disconnectIfNeeded() {
        val current = connectionManager.state.value
        if (current is ObdConnectionState.Disconnected) return
        val currentConfig = current.configOrNull() ?: return
        val entryWasConnected = connectionAtEntry is ObdConnectionState.Connected
        if (entryWasConnected && connectionAtEntry.configOrNull() == currentConfig) return
        connectionManager.disconnect()
    }

    private fun cancelSetupAndShowAdapter() {
        setupJob?.cancel()
        setupJob = null
        viewModelScope.launch { disconnectIfNeeded() }
        resetAttemptState()
        openAdapterStep()
    }

    private fun openAdapterStep() {
        setForm(
            lastForm.copy(
                step = FormStep.ADAPTER,
                errors = FormErrors(),
            ),
        )
    }

    private fun resetAttemptState() {
        attemptedConnection = false
        connectedAtMillis = null
        ecuVerifiedAtMillis = null
        vehicleIdentity = null
    }

    private suspend fun readIdentitySafely(): VehicleIdentity? =
        try {
            readVehicleIdentity()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }

    private fun currentForm(): VehicleSetupState.Form? =
        (_state.value as? VehicleSetupState.Form) ?: lastForm

    private fun updateForm(transform: (VehicleSetupState.Form) -> VehicleSetupState.Form) {
        val current = currentForm() ?: return
        setForm(transform(current))
    }

    private fun setForm(form: VehicleSetupState.Form) {
        lastForm = form
        _state.value = form
    }

    private fun ObdConnectionState.configOrNull(): ObdWifiConfig? =
        when (this) {
            is ObdConnectionState.Connected -> config
            is ObdConnectionState.Initializing -> config
            is ObdConnectionState.Connecting -> config
            is ObdConnectionState.Failed -> config
            is ObdConnectionState.Disconnected -> null
        }

    companion object {
        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    AddVehicleViewModel(
                        vehicleRepository = container.vehicleRepository,
                        settingsRepository = container.settingsRepository,
                        connectionManager = container.obdConnectionManager,
                        discoverPids = { onProgress ->
                            container.pidDiscoveryCoordinator.discover(onProgress)
                        },
                        readVehicleIdentity = container.vehicleIdentityExtractor::read,
                        metricsController = container.metricsCollectionController,
                    )
                }
            }
    }
}
