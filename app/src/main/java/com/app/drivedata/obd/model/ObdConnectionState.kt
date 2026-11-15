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
    INVALID_RESPONSE,
}

enum class ConnectionFailure {
    INVALID_CONFIGURATION,
    CONNECT_TIMEOUT,
    TRANSPORT,
    MISSING_PROMPT,
    ADAPTER_INITIALIZATION,
    ECU_NO_RESPONSE,
    UNKNOWN,
}

sealed interface ObdConnectionState {
    /** True while a session is live or being established (Connecting, Initializing, Connected). */
    val isSessionActive: Boolean
        get() = this is Connecting || this is Initializing || this is Connected

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
