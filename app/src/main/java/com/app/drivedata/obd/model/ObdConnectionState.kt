package com.app.drivedata.obd.model

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

enum class EcuStatus {
    UNKNOWN,
    VERIFYING,
    VERIFIED,
    NO_COMMUNICATION,
}

enum class ConnectionFailure {
    INVALID_CONFIGURATION,
    TRANSPORT,
    ADAPTER_INITIALIZATION,
    UNKNOWN,
}

sealed interface ObdConnectionState {
    data object Disconnected : ObdConnectionState

    data class Connecting(val config: ObdWifiConfig) : ObdConnectionState

    data class Initializing(val config: ObdWifiConfig) : ObdConnectionState

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
