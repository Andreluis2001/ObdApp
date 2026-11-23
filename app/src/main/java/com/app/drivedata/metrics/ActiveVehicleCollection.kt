package com.app.drivedata.metrics

import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.vehicle.model.Vehicle

/**
 * The active vehicle as far as metrics collection is concerned.
 *
 * [catalog] contains only catalog metrics whose PID was discovered for the vehicle and is still
 * enabled for collection. [host]/[port] identify the adapter the readings must come from, so a
 * connection to a different vehicle's adapter never collects this vehicle's PIDs. [vin] is the
 * identity this vehicle was discovered with; the metrics stream validates the ECU's VIN against it.
 */
data class ActiveVehicleCollection(
    val vehicleId: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val vin: String?,
    val hasDiscovery: Boolean,
    val catalog: List<MetricDefinition>,
) {
    val canCollect: Boolean
        get() = hasDiscovery && catalog.isNotEmpty()

    /** Saved adapter address, reused when collection must reconnect after a pause. */
    val adapterConfig: ObdWifiConfig?
        get() = if (host.isNotBlank() && port in 1..65535) ObdWifiConfig(host, port) else null

    fun matches(config: ObdWifiConfig): Boolean =
        host == config.host.trim() && port == config.port
}

fun Vehicle.toActiveVehicleCollection(
    catalog: List<MetricDefinition> = defaultMetricCatalog,
): ActiveVehicleCollection {
    val enabledPids = capabilities.enabledPids
    return ActiveVehicleCollection(
        vehicleId = id,
        displayName = displayName,
        host = connection.host,
        port = connection.port,
        vin = identity?.vin?.trim()?.ifBlank { null },
        hasDiscovery = capabilities.hasDiscovery,
        catalog = catalog.filter { it.pid in enabledPids },
    )
}
