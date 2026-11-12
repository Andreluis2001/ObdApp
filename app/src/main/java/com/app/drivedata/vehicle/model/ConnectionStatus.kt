package com.app.drivedata.vehicle.model

/** Durable connection outcome for a vehicle's ELM327 adapter. */
enum class ConnectionStatus {
    NOT_CONFIGURED,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}
