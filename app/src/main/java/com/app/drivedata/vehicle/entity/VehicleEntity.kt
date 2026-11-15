package com.app.drivedata.vehicle.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.SupportedPid
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleCapabilities
import com.app.drivedata.vehicle.model.VehicleDetails

@Entity(
    tableName = "vehicles",
    indices = [
        Index(
            name = "index_vehicles_owner_active",
            value = ["owner_username", "is_active"],
        ),
    ],
)
data class VehicleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    val make: String,
    val model: String,
    @ColumnInfo(name = "license_plate") val licensePlate: String?,
    @ColumnInfo(name = "is_active", defaultValue = "0") val isActive: Boolean,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)

internal fun VehicleEntity.toVehicle(
    connection: Elm327ConnectionEntity?,
    supportedPids: List<SupportedPidEntity>,
): Vehicle =
    Vehicle(
        id = id,
        ownerUsername = ownerUsername,
        details =
            VehicleDetails(
                make = make,
                model = model,
                licensePlate = licensePlate,
            ),
        connection = connection?.toModel() ?: Elm327Connection.unconfigured(),
        capabilities =
            VehicleCapabilities(
                supportedPids =
                    supportedPids.sortedBy { it.pid }.map {
                        SupportedPid(pid = it.pid, enabledForCollection = it.enabledForCollection)
                    },
                lastDiscoveredAtMillis = supportedPids.maxOfOrNull { it.discoveredAtMillis },
            ),
        isActive = isActive,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
