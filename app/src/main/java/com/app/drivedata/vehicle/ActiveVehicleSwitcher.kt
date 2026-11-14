package com.app.drivedata.vehicle

import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.vehicle.model.Vehicle

/**
 * Activates a vehicle, dropping any live adapter link that belongs to another vehicle.
 *
 * The app never auto-connects, so keeping a link to the previous vehicle's adapter after switching
 * would be misleading. Collection is already blocked when the adapter does not match the active
 * vehicle, but the stale connection would still appear as connected.
 */
class ActiveVehicleSwitcher(
    private val vehicleRepository: VehicleRepository,
    private val connectionManager: ObdConnectionManager,
) {
    suspend fun switchTo(vehicle: Vehicle): Boolean {
        if (
            connectionManager.state.value !is ObdConnectionState.Disconnected &&
            !isConnectedTo(vehicle)
        ) {
            connectionManager.disconnect()
        }
        return vehicleRepository.setActive(vehicle.id)
    }

    private fun isConnectedTo(vehicle: Vehicle): Boolean {
        val connection = vehicle.connection
        if (!connection.isConfigured) return false
        val live = connectionManager.state.value.configOrNull() ?: return false
        return live.host == connection.host && live.port == connection.port
    }
}

private fun ObdConnectionState.configOrNull(): ObdWifiConfig? =
    when (this) {
        is ObdConnectionState.Connected -> config
        is ObdConnectionState.Initializing -> config
        is ObdConnectionState.Connecting -> config
        is ObdConnectionState.Failed -> config
        is ObdConnectionState.Disconnected -> null
    }
