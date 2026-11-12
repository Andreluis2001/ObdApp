package com.app.drivedata.vehicle.model

/** A PID advertised by the vehicle ECU, stored by its raw OBD PID byte (e.g. 0x0C for RPM). */
data class SupportedPid(
    val pid: Int,
    val enabledForCollection: Boolean = true,
)

data class VehicleCapabilities(
    val supportedPids: List<SupportedPid> = emptyList(),
    val lastDiscoveredAtMillis: Long? = null,
) {
    val hasDiscovery: Boolean
        get() = lastDiscoveredAtMillis != null

    val enabledPids: Set<Int>
        get() = supportedPids.filter { it.enabledForCollection }.map { it.pid }.toSet()
}
