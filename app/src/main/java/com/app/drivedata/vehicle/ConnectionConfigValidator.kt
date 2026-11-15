package com.app.drivedata.vehicle

import com.app.drivedata.obd.model.ObdWifiConfig

/** Raw adapter address values, before trimming. */
data class ConnectionDraft(
    val host: String = "",
    val port: String = "",
)

data class ConnectionConfigErrors(
    val host: Boolean = false,
    val port: Boolean = false,
) {
    val hasErrors: Boolean
        get() = host || port
}

sealed interface ConnectionConfigValidation {
    data class Valid(val config: ObdWifiConfig) : ConnectionConfigValidation

    /** Both fields are blank: the vehicle can be saved without an adapter configuration. */
    data object Unconfigured : ConnectionConfigValidation

    data class Invalid(val errors: ConnectionConfigErrors) : ConnectionConfigValidation
}

/** Validates the adapter IPv4 address and TCP port entered on the vehicle setup screen. */
object ConnectionConfigValidator {

    fun validate(host: String, port: String): ConnectionConfigValidation {
        val trimmedHost = host.trim()
        val trimmedPort = port.trim()
        if (trimmedHost.isEmpty() && trimmedPort.isEmpty()) {
            return ConnectionConfigValidation.Unconfigured
        }

        val hostValid = isValidHost(trimmedHost)
        val portNumber = trimmedPort.toIntOrNull()
        val portValid = portNumber != null && portNumber in MIN_PORT..MAX_PORT
        if (hostValid && portNumber != null && portValid) {
            return ConnectionConfigValidation.Valid(
                ObdWifiConfig(host = trimmedHost, port = portNumber),
            )
        }
        return ConnectionConfigValidation.Invalid(
            ConnectionConfigErrors(host = !hostValid, port = !portValid),
        )
    }

    fun isValidHost(host: String): Boolean = IPV4_PATTERN.matches(host.trim())

    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535

    private val IPV4_PATTERN =
        Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}" +
                "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$",
        )
}
