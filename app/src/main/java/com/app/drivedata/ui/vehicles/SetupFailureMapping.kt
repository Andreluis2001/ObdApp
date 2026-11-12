package com.app.drivedata.ui.vehicles

import com.app.drivedata.obd.model.ConnectionFailure
import com.app.drivedata.obd.model.DiscoveryFailure

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
