package com.app.drivedata.ui.discovery

import com.app.drivedata.R
import com.app.drivedata.obd.model.ConnectionFailure
import com.app.drivedata.obd.model.DiscoveryFailure

/** Stages of the vehicle discovery flow, from opening the adapter to persisting the vehicle. */
enum class SetupStage {
    CONNECT,
    INITIALIZE,
    ECU_TEST,
    IDENTITY,
    DISCOVERY,
    SAVE,
}

/** User-facing failure categories for the discovery flow. */
enum class SetupFailure {
    INVALID_CONFIGURATION,
    CONNECT_TIMEOUT,
    TRANSPORT,
    MISSING_PROMPT,
    ADAPTER_INITIALIZATION,
    ECU_NO_DATA,
    MALFORMED_RESPONSE,
    VIN_UNAVAILABLE,
    SERVICE_MISMATCH,
    TRUNCATED,
    TIMEOUT,
    CONNECTION_LOST,
    SAVE_FAILED,
    UNKNOWN,
}

internal fun ConnectionFailure?.setupStage(): SetupStage =
    when (this) {
        ConnectionFailure.MISSING_PROMPT,
        ConnectionFailure.ADAPTER_INITIALIZATION,
        -> SetupStage.INITIALIZE

        ConnectionFailure.ECU_NO_RESPONSE -> SetupStage.ECU_TEST
        else -> SetupStage.CONNECT
    }

internal fun ConnectionFailure?.toSetupFailure(): SetupFailure =
    when (this) {
        ConnectionFailure.INVALID_CONFIGURATION -> SetupFailure.INVALID_CONFIGURATION
        ConnectionFailure.CONNECT_TIMEOUT -> SetupFailure.CONNECT_TIMEOUT
        ConnectionFailure.TRANSPORT -> SetupFailure.TRANSPORT
        ConnectionFailure.MISSING_PROMPT -> SetupFailure.MISSING_PROMPT
        ConnectionFailure.ADAPTER_INITIALIZATION -> SetupFailure.ADAPTER_INITIALIZATION
        ConnectionFailure.ECU_NO_RESPONSE -> SetupFailure.ECU_NO_DATA
        ConnectionFailure.UNKNOWN, null -> SetupFailure.UNKNOWN
    }

internal fun DiscoveryFailure.toSetupFailure(): SetupFailure =
    when (this) {
        DiscoveryFailure.ECU_NO_DATA -> SetupFailure.ECU_NO_DATA
        DiscoveryFailure.SERVICE_MISMATCH -> SetupFailure.SERVICE_MISMATCH
        DiscoveryFailure.TRUNCATED -> SetupFailure.TRUNCATED
        DiscoveryFailure.MALFORMED -> SetupFailure.MALFORMED_RESPONSE
        DiscoveryFailure.TIMEOUT -> SetupFailure.TIMEOUT
        DiscoveryFailure.CONNECTION_LOST -> SetupFailure.CONNECTION_LOST
        DiscoveryFailure.UNKNOWN -> SetupFailure.UNKNOWN
    }

internal fun SetupStage.labelRes(): Int =
    when (this) {
        SetupStage.CONNECT -> R.string.discovery_stage_connect
        SetupStage.INITIALIZE -> R.string.discovery_stage_initialize
        SetupStage.ECU_TEST -> R.string.discovery_stage_ecu
        SetupStage.IDENTITY -> R.string.discovery_stage_identity
        SetupStage.DISCOVERY -> R.string.discovery_stage_discovery
        SetupStage.SAVE -> R.string.discovery_stage_save
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
        SetupFailure.VIN_UNAVAILABLE -> R.string.failure_vin_unavailable
        SetupFailure.SERVICE_MISMATCH, SetupFailure.TRUNCATED -> R.string.failure_invalid_bitmap
        SetupFailure.TIMEOUT -> R.string.failure_discovery_timeout
        SetupFailure.CONNECTION_LOST -> R.string.failure_discovery_connection_lost
        SetupFailure.SAVE_FAILED -> R.string.failure_save
        SetupFailure.UNKNOWN -> R.string.failure_unknown
    }
