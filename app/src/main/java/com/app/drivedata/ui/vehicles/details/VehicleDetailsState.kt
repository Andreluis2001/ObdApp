package com.app.drivedata.ui.vehicles.details

import com.app.drivedata.ui.discovery.SetupFailure
import com.app.drivedata.ui.discovery.SetupStage
import com.app.drivedata.vehicle.model.Vehicle

data class VehicleDetailsUiState(
    val vehicle: Vehicle? = null,
    val isLoading: Boolean = true,
    val operation: DetailsOperation = DetailsOperation.Idle,
    val error: DetailsError? = null,
    val removed: Boolean = false,
) {
    val isBusy: Boolean
        get() = operation !is DetailsOperation.Idle
}

sealed interface DetailsOperation {
    data object Idle : DetailsOperation

    data object Connecting : DetailsOperation

    data class Discovering(val rangeBase: Int, val foundCount: Int) : DetailsOperation
}

sealed interface DetailsError {
    data object NotConfigured : DetailsError

    data class Rediscovery(val stage: SetupStage, val failure: SetupFailure) : DetailsError

    data object PidUpdateFailed : DetailsError

    data object DeleteFailed : DetailsError

    data object ActivateFailed : DetailsError
}
