package com.app.drivedata.vehicle.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "supported_pids",
    primaryKeys = ["vehicle_id", "pid"],
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "index_supported_pids_vehicle_id", value = ["vehicle_id"])],
)
data class SupportedPidEntity(
    @ColumnInfo(name = "vehicle_id") val vehicleId: String,
    val pid: Int,
    @ColumnInfo(name = "discovered_at_millis") val discoveredAtMillis: Long,
    @ColumnInfo(name = "enabled_for_collection", defaultValue = "1")
    val enabledForCollection: Boolean,
)
