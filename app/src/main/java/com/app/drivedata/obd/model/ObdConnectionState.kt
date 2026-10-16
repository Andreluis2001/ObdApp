package com.app.drivedata.obd.model

/** Connection target for a Wi-Fi ELM327 bridge. */
data class ObdWifiConfig(
    val host: String,
    val port: Int,
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port in 1..MAX_PORT

    private companion object {
        const val MAX_PORT = 65535
    }
}

/** Outcome of talking to the vehicle's ECU over the adapter. */
enum class EcuStatus {
    UNKNOWN,
    VERIFYING,
    VERIFIED,
    NO_COMMUNICATION,
}

/** Categories of connection failure, mapped to user strings by the UI. */
enum class ConnectionFailure {
    INVALID_CONFIGURATION,
    TRANSPORT,
    ADAPTER_INITIALIZATION,
    UNKNOWN,
}

/**
 * User-visible state of the adapter connection and ECU verification.
 *
 * A success state is only reported after the corresponding operation actually succeeded;
 * an unreachable ECU is shown explicitly and never as a zero reading.
 */
sealed interface ObdConnectionState {
    data object Disconnected : ObdConnectionState

    data class Connecting(val config: ObdWifiConfig) : ObdConnectionState

    /** Adapter reached; running the ELM327 setup sequence. */
    data class Initializing(val config: ObdWifiConfig) : ObdConnectionState

    /** Adapter initialized and ECU verification finished. */
    data class Connected(
        val config: ObdWifiConfig,
        val protocol: String?,
        val ecuStatus: EcuStatus,
    ) : ObdConnectionState

    data class Failed(
        val config: ObdWifiConfig?,
        val reason: ConnectionFailure,
    ) : ObdConnectionState
}
