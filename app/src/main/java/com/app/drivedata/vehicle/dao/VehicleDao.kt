package com.app.drivedata.vehicle.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.app.drivedata.vehicle.entity.Elm327ConnectionEntity
import com.app.drivedata.vehicle.entity.SupportedPidEntity
import com.app.drivedata.vehicle.entity.VehicleEntity
import com.app.drivedata.vehicle.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Transaction
    @Query("SELECT * FROM vehicles WHERE owner_username = :owner ORDER BY created_at_millis ASC")
    fun observeVehicles(owner: String): Flow<List<VehicleWithConnection>>

    @Transaction
    @Query(
        "SELECT * FROM vehicles WHERE owner_username = :owner AND is_active = 1 LIMIT 1",
    )
    fun observeActiveVehicle(owner: String): Flow<VehicleWithConnection?>

    @Transaction
    @Query("SELECT * FROM vehicles WHERE id = :vehicleId LIMIT 1")
    fun observeVehicle(vehicleId: String): Flow<VehicleWithConnection?>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId LIMIT 1")
    suspend fun findVehicle(vehicleId: String): VehicleEntity?

    @Query("SELECT * FROM elm327_connections WHERE vehicle_id = :vehicleId LIMIT 1")
    suspend fun findConnection(vehicleId: String): Elm327ConnectionEntity?

    @Query("SELECT COUNT(*) FROM vehicles WHERE owner_username = :owner")
    suspend fun countVehicles(owner: String): Int

    @Query("SELECT COUNT(*) FROM vehicles WHERE owner_username = :owner AND is_active = 1")
    suspend fun countActiveVehicles(owner: String): Int

    @Upsert suspend fun upsertVehicle(vehicle: VehicleEntity)

    @Upsert suspend fun upsertConnection(connection: Elm327ConnectionEntity)

    @Transaction
    suspend fun insertVehicle(
        vehicle: VehicleEntity,
        connection: Elm327ConnectionEntity?,
        pids: List<SupportedPidEntity> = emptyList(),
    ) {
        upsertVehicle(vehicle)
        if (connection != null) upsertConnection(connection)
        if (pids.isNotEmpty()) insertSupportedPids(pids)
    }

    @Query("UPDATE vehicles SET is_active = 0 WHERE owner_username = :owner")
    suspend fun clearActive(owner: String)

    @Query("UPDATE vehicles SET is_active = 1 WHERE id = :vehicleId AND owner_username = :owner")
    suspend fun markActive(vehicleId: String, owner: String): Int

    @Transaction
    suspend fun setActive(owner: String, vehicleId: String): Boolean {
        val target = findVehicle(vehicleId) ?: return false
        if (target.ownerUsername != owner) return false
        clearActive(owner)
        return markActive(vehicleId, owner) > 0
    }

    @Query(
        "UPDATE vehicles SET make = :make, model = :model, " +
            "license_plate = :licensePlate, updated_at_millis = :updatedAtMillis " +
            "WHERE id = :vehicleId",
    )
    suspend fun updateDetails(
        vehicleId: String,
        make: String,
        model: String,
        licensePlate: String?,
        updatedAtMillis: Long,
    ): Int

    @Query(
        "UPDATE elm327_connections SET host = :host, port = :port, connection_status = :status " +
            "WHERE vehicle_id = :vehicleId",
    )
    suspend fun updateConnectionConfig(
        vehicleId: String,
        host: String,
        port: Int,
        status: ConnectionStatus,
    ): Int

    @Query(
        "UPDATE elm327_connections SET connection_status = :status, " +
            "last_connection_at_millis = :atMillis WHERE vehicle_id = :vehicleId",
    )
    suspend fun markAdapterConnected(
        vehicleId: String,
        status: ConnectionStatus,
        atMillis: Long,
    ): Int

    @Query(
        "UPDATE elm327_connections SET connection_status = :status, " +
            "last_ecu_communication_at_millis = :atMillis WHERE vehicle_id = :vehicleId",
    )
    suspend fun markEcuVerified(
        vehicleId: String,
        status: ConnectionStatus,
        atMillis: Long,
    ): Int

    @Query(
        "UPDATE elm327_connections SET connection_status = :status WHERE vehicle_id = :vehicleId",
    )
    suspend fun updateConnectionStatus(vehicleId: String, status: ConnectionStatus): Int

    @Query("DELETE FROM vehicles WHERE id = :vehicleId AND owner_username = :owner")
    suspend fun deleteVehicle(vehicleId: String, owner: String): Int

    @Query("SELECT * FROM supported_pids WHERE vehicle_id = :vehicleId ORDER BY pid ASC")
    suspend fun supportedPids(vehicleId: String): List<SupportedPidEntity>

    @Query("DELETE FROM supported_pids WHERE vehicle_id = :vehicleId")
    suspend fun deleteSupportedPids(vehicleId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupportedPids(pids: List<SupportedPidEntity>)

    @Transaction
    suspend fun replaceSupportedPids(vehicleId: String, pids: List<SupportedPidEntity>) {
        deleteSupportedPids(vehicleId)
        insertSupportedPids(pids)
    }

    @Query(
        "UPDATE supported_pids SET enabled_for_collection = :enabled " +
            "WHERE vehicle_id = :vehicleId AND pid = :pid",
    )
    suspend fun setPidEnabled(vehicleId: String, pid: Int, enabled: Boolean): Int
}
