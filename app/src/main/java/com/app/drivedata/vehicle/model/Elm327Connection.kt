package com.app.drivedata.vehicle.model

data class Elm327Connection(
    val host: String,
    val port: Int,
    val lastConnectionAtMillis: Long? = null,
    val lastEcuCommunicationAtMillis: Long? = null,
    val status: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED,
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && port in 1..65535

    companion object {
        fun unconfigured(): Elm327Connection = Elm327Connection(host = "", port = 0)
    }
}
