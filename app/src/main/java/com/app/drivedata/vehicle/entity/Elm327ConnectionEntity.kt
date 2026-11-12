package com.app.drivedata.vehicle.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.app.drivedata.vehicle.model.ConnectionStatus
import com.app.drivedata.vehicle.model.Elm327Connection

@Entity(
    tableName = "elm327_connections",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Elm327ConnectionEntity(
    @PrimaryKey @ColumnInfo(name = "vehicle_id") val vehicleId: String,
    val host: String,
    val port: Int,
    @ColumnInfo(name = "last_connection_at_millis") val lastConnectionAtMillis: Long? = null,
    @ColumnInfo(name = "last_ecu_communication_at_millis") val lastEcuCommunicationAtMillis: Long? =
        null,
    @ColumnInfo(name = "connection_status") val connectionStatus: ConnectionStatus,
)

internal fun Elm327ConnectionEntity.toModel(): Elm327Connection =
    Elm327Connection(
        host = host,
        port = port,
        lastConnectionAtMillis = lastConnectionAtMillis,
        lastEcuCommunicationAtMillis = lastEcuCommunicationAtMillis,
        status = connectionStatus,
    )
