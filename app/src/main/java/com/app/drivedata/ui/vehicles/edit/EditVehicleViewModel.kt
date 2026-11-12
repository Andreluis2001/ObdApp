package com.app.drivedata.ui.vehicles.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.settings.AppSettingsRepository
import com.app.drivedata.ui.vehicles.FormErrors
import com.app.drivedata.vehicle.ConnectionConfigValidation
import com.app.drivedata.vehicle.ConnectionConfigValidator
import com.app.drivedata.vehicle.ConnectionDraft
import com.app.drivedata.vehicle.VehicleDetailsDraft
import com.app.drivedata.vehicle.VehicleDetailsValidation
import com.app.drivedata.vehicle.VehicleDetailsValidator
import com.app.drivedata.vehicle.VehicleRepository
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditVehicleUiState(
    val draft: VehicleDetailsDraft = VehicleDetailsDraft(),
    val connection: ConnectionDraft = ConnectionDraft(),
    val errors: FormErrors = FormErrors(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
)

/**
 * Edits the vehicle information and, when provided, its adapter address.
 *
 * A changed adapter address is stored as not configured, so the details screen requires a new
 * rediscovery before the vehicle is considered verified again.
 */
class EditVehicleViewModel(
    private val vehicleId: String,
    private val vehicleRepository: VehicleRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditVehicleUiState())
    val state: StateFlow<EditVehicleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val vehicle = vehicleRepository.observeVehicle(vehicleId).first()
            _state.value =
                if (vehicle == null) {
                    _state.value.copy(isLoading = false, loadFailed = true)
                } else {
                    _state.value.copy(
                        draft = vehicle.details.toDraft(),
                        connection = vehicle.toConnectionDraft(),
                        isLoading = false,
                    )
                }
        }
    }

    fun onMakeChange(value: String) {
        _state.value =
            _state.value.copy(
                draft = _state.value.draft.copy(make = value),
                errors = _state.value.errors.copy(details = _state.value.errors.details.copy(make = false)),
                saveFailed = false,
            )
    }

    fun onModelChange(value: String) {
        _state.value =
            _state.value.copy(
                draft = _state.value.draft.copy(model = value),
                errors = _state.value.errors.copy(details = _state.value.errors.details.copy(model = false)),
                saveFailed = false,
            )
    }

    fun onYearChange(value: String) {
        val digits = value.filter(Char::isDigit).take(4)
        _state.value =
            _state.value.copy(
                draft = _state.value.draft.copy(year = digits),
                errors = _state.value.errors.copy(details = _state.value.errors.details.copy(year = false)),
                saveFailed = false,
            )
    }

    fun onPlateChange(value: String) {
        val normalized =
            value.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == ' ' }.take(10)
        _state.value =
            _state.value.copy(
                draft = _state.value.draft.copy(licensePlate = normalized),
                errors = _state.value.errors.copy(details = _state.value.errors.details.copy(licensePlate = false)),
                saveFailed = false,
            )
    }

    fun onNicknameChange(value: String) {
        _state.value =
            _state.value.copy(
                draft = _state.value.draft.copy(nickname = value),
                errors = _state.value.errors.copy(details = _state.value.errors.details.copy(nickname = false)),
                saveFailed = false,
            )
    }

    fun onHostChange(value: String) {
        _state.value =
            _state.value.copy(
                connection = _state.value.connection.copy(host = value),
                errors = _state.value.errors.copy(connection = _state.value.errors.connection.copy(host = false)),
                saveFailed = false,
            )
    }

    fun onPortChange(value: String) {
        val digits = value.filter(Char::isDigit).take(5)
        _state.value =
            _state.value.copy(
                connection = _state.value.connection.copy(port = digits),
                errors = _state.value.errors.copy(connection = _state.value.errors.connection.copy(port = false)),
                saveFailed = false,
            )
    }

    fun save() {
        if (_state.value.isSaving) return
        val current = _state.value

        val details =
            when (val validation = VehicleDetailsValidator.validate(current.draft)) {
                is VehicleDetailsValidation.Valid -> validation.details
                is VehicleDetailsValidation.Invalid -> {
                    _state.value =
                        current.copy(errors = current.errors.copy(details = validation.errors))
                    return
                }
            }

        val connectionValidation =
            ConnectionConfigValidator.validate(current.connection.host, current.connection.port)
        if (connectionValidation is ConnectionConfigValidation.Invalid) {
            _state.value =
                current.copy(errors = current.errors.copy(connection = connectionValidation.errors))
            return
        }

        _state.value = current.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val detailsSaved = vehicleRepository.updateDetails(vehicleId, details)
            var connectionSaved = true
            if (connectionValidation is ConnectionConfigValidation.Valid) {
                connectionSaved =
                    vehicleRepository.saveConnectionConfig(
                        vehicleId = vehicleId,
                        host = connectionValidation.config.host,
                        port = connectionValidation.config.port,
                    )
                if (connectionSaved) {
                    runCatching { settingsRepository.saveWifiConfig(connectionValidation.config) }
                }
            }
            _state.value =
                if (detailsSaved && connectionSaved) {
                    _state.value.copy(isSaving = false, saved = true)
                } else {
                    _state.value.copy(isSaving = false, saveFailed = true)
                }
        }
    }

    fun dismissSaveError() {
        _state.value = _state.value.copy(saveFailed = false)
    }

    fun consumeSaved() {
        _state.value = _state.value.copy(saved = false)
    }

    private fun Vehicle.toConnectionDraft(): ConnectionDraft =
        if (connection.isConfigured) {
            ConnectionDraft(host = connection.host, port = connection.port.toString())
        } else {
            ConnectionDraft()
        }

    private fun VehicleDetails.toDraft(): VehicleDetailsDraft =
        VehicleDetailsDraft(
            make = make,
            model = model,
            year = year?.toString().orEmpty(),
            licensePlate = licensePlate.orEmpty(),
            nickname = nickname.orEmpty(),
        )

    companion object {
        fun factory(
            application: DriveDataApplication,
            vehicleId: String,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    EditVehicleViewModel(
                        vehicleId = vehicleId,
                        vehicleRepository = container.vehicleRepository,
                        settingsRepository = container.settingsRepository,
                    )
                }
            }
    }
}
