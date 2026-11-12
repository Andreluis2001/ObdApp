package com.app.drivedata.ui.vehicles

import com.app.drivedata.R

internal fun SetupStage.labelRes(): Int =
    when (this) {
        SetupStage.CONNECT -> R.string.add_vehicle_stage_connect
        SetupStage.INITIALIZE -> R.string.add_vehicle_stage_initialize
        SetupStage.ECU_TEST -> R.string.add_vehicle_stage_ecu
        SetupStage.DISCOVERY -> R.string.add_vehicle_stage_discovery
        SetupStage.SAVE -> R.string.add_vehicle_stage_save
    }

internal fun SetupFailure.messageRes(): Int =
    when (this) {
        SetupFailure.INVALID_CONFIGURATION -> R.string.failure_invalid_configuration
        SetupFailure.CONNECT_TIMEOUT -> R.string.failure_connect_timeout
        SetupFailure.TRANSPORT -> R.string.failure_transport
        SetupFailure.MISSING_PROMPT -> R.string.failure_missing_prompt
        SetupFailure.ADAPTER_INITIALIZATION -> R.string.failure_adapter_initialization
        SetupFailure.ECU_NO_DATA -> R.string.failure_ecu_no_data
        SetupFailure.MALFORMED_RESPONSE -> R.string.failure_malformed_response
        SetupFailure.SERVICE_MISMATCH, SetupFailure.TRUNCATED -> R.string.failure_invalid_bitmap
        SetupFailure.TIMEOUT -> R.string.failure_discovery_timeout
        SetupFailure.CONNECTION_LOST -> R.string.failure_discovery_connection_lost
        SetupFailure.SAVE_FAILED -> R.string.failure_save
        SetupFailure.UNKNOWN -> R.string.failure_unknown
    }
