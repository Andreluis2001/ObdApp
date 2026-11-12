package com.app.drivedata.vehicle.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.app.drivedata.vehicle.entity.Elm327ConnectionEntity
import com.app.drivedata.vehicle.entity.SupportedPidEntity
import com.app.drivedata.vehicle.entity.VehicleEntity
import com.app.drivedata.vehicle.entity.toVehicle
import com.app.drivedata.vehicle.model.Vehicle

data class VehicleWithConnection(
    @Embedded val vehicle: VehicleEntity,
    @Relation(parentColumn = "id", entityColumn = "vehicle_id")
    val connection: Elm327ConnectionEntity?,
    @Relation(parentColumn = "id", entityColumn = "vehicle_id")
    val supportedPids: List<SupportedPidEntity>,
) {
    fun toVehicle(): Vehicle = vehicle.toVehicle(connection, supportedPids)
}
