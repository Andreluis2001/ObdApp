package com.app.drivedata.ui.vehicles

import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.vehicle.ConnectionConfigErrors
import com.app.drivedata.vehicle.ConnectionDraft
import com.app.drivedata.vehicle.VehicleDetailsDraft
import com.app.drivedata.vehicle.VehicleDetailsErrors

enum class FormStep { VEHICLE, ADAPTER }

data class FormErrors(
    val details: VehicleDetailsErrors = VehicleDetailsErrors(),
    val connection: ConnectionConfigErrors = ConnectionConfigErrors(),
) {
    val hasErrors: Boolean
        get() = details.hasErrors || connection.hasErrors
}

/** A catalog PID present in the discovered bitmap, with its review toggle. */
data class DiscoveredPid(
    val pid: Int,
    val metricId: MetricId,
    val enabled: Boolean = true,
)

sealed interface VehicleSetupState {
    data class Form(
        val step: FormStep = FormStep.VEHICLE,
        val details: VehicleDetailsDraft = VehicleDetailsDraft(),
        val connection: ConnectionDraft = ConnectionDraft(),
        val errors: FormErrors = FormErrors(),
    ) : VehicleSetupState

    data object ConnectingAdapter : VehicleSetupState

    data object InitializingAdapter : VehicleSetupState

    data object TestingEcu : VehicleSetupState

    data class DiscoveringPids(val rangeBase: Int, val foundCount: Int) : VehicleSetupState

    data class Review(
        val pids: List<DiscoveredPid>,
        val discoveredPids: Set<Int>,
        val discoveredAtMillis: Long,
    ) : VehicleSetupState

    data class Failed(val stage: SetupStage, val failure: SetupFailure) : VehicleSetupState

    data object Saving : VehicleSetupState
}

enum class SetupStage {
    CONNECT,
    INITIALIZE,
    ECU_TEST,
    DISCOVERY,
    SAVE,
}

enum class SetupFailure {
    INVALID_CONFIGURATION,
    CONNECT_TIMEOUT,
    TRANSPORT,
    MISSING_PROMPT,
    ADAPTER_INITIALIZATION,
    ECU_NO_DATA,
    MALFORMED_RESPONSE,
    SERVICE_MISMATCH,
    TRUNCATED,
    TIMEOUT,
    CONNECTION_LOST,
    SAVE_FAILED,
    UNKNOWN,
}

sealed interface AddVehicleEvent {
    data class Saved(val vehicleId: String) : AddVehicleEvent

    data object Cancelled : AddVehicleEvent
}
