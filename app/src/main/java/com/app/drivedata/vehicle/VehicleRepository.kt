package com.app.drivedata.vehicle

import com.app.drivedata.auth.AuthSession
import com.app.drivedata.vehicle.dao.VehicleDao
import com.app.drivedata.vehicle.entity.Elm327ConnectionEntity
import com.app.drivedata.vehicle.entity.SupportedPidEntity
import com.app.drivedata.vehicle.entity.VehicleEntity
import com.app.drivedata.vehicle.entity.toVehicle
import com.app.drivedata.vehicle.model.ConnectionStatus
import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleDetails
import com.app.drivedata.vehicle.model.VehicleIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Persists vehicles, their ELM327 connection settings and discovered PID capabilities.
 *
 * Every read and write is scoped to the signed-in user; operations return `false`/`null` when
 * there is no session or the vehicle belongs to another user.
 */
class VehicleRepository(
    private val dao: VehicleDao,
    private val authSession: AuthSession,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVehicles(): Flow<List<Vehicle>> =
        authSession.signedInUser.flatMapLatest { owner ->
            if (owner == null) {
                flowOf(emptyList())
            } else {
                dao.observeVehicles(owner).map { rows -> rows.map { it.toVehicle() } }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveVehicle(): Flow<Vehicle?> =
        authSession.signedInUser.flatMapLatest { owner ->
            if (owner == null) {
                flowOf(null)
            } else {
                dao.observeActiveVehicle(owner).map { it?.toVehicle() }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVehicle(vehicleId: String): Flow<Vehicle?> =
        authSession.signedInUser.flatMapLatest { owner ->
            if (owner == null) {
                flowOf(null)
            } else {
                dao.observeVehicle(vehicleId).map { row ->
                    row?.takeIf { it.vehicle.ownerUsername == owner }?.toVehicle()
                }
            }
        }

    /**
     * Inserts a vehicle, activating it when it is the owner's first one. [connection] is stored only
     * when it has a usable host and port.
     *
     * [discoveredPids] and [disabledPids] are written in the same Room transaction as the vehicle
     * and its connection, so a new vehicle is either fully configured or not created at all.
     */
    suspend fun create(
        details: VehicleDetails,
        identity: VehicleIdentity? = null,
        connection: Elm327Connection? = null,
        discoveredPids: Set<Int>? = null,
        discoveredAtMillis: Long = clock(),
        disabledPids: Set<Int> = emptySet(),
    ): Vehicle? {
        val owner = currentOwner() ?: return null
        val now = clock()
        val vehicle =
            VehicleEntity(
                id = idGenerator(),
                ownerUsername = owner,
                make = details.make,
                model = details.model,
                licensePlate = details.licensePlate,
                vin = identity?.vin,
                manufacturer = identity?.manufacturer,
                serialNumber = identity?.serialNumber,
                isActive = dao.countVehicles(owner) == 0,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        val connectionEntity = connection?.takeIf { it.isConfigured }?.toEntity(vehicle.id)
        val pidEntities =
            discoveredPids
                ?.sorted()
                ?.map { pid ->
                    SupportedPidEntity(
                        vehicleId = vehicle.id,
                        pid = pid,
                        discoveredAtMillis = discoveredAtMillis,
                        enabledForCollection = pid !in disabledPids,
                    )
                }
                .orEmpty()
        dao.insertVehicle(vehicle, connectionEntity, pidEntities)
        return vehicle.toVehicle(connectionEntity, pidEntities)
    }

    suspend fun updateDetails(vehicleId: String, details: VehicleDetails): Boolean {
        if (!isOwned(vehicleId)) return false
        return dao.updateDetails(
            vehicleId = vehicleId,
            make = details.make,
            model = details.model,
            licensePlate = details.licensePlate,
            updatedAtMillis = clock(),
        ) > 0
    }

    suspend fun setActive(vehicleId: String): Boolean {
        val owner = currentOwner() ?: return false
        if (!isOwned(vehicleId)) return false
        return dao.setActive(owner, vehicleId)
    }

    suspend fun delete(vehicleId: String): Boolean {
        val owner = currentOwner() ?: return false
        return dao.deleteVehicle(vehicleId, owner) > 0
    }

    /** Stores new adapter settings. Changing host/port invalidates any previous verification. */
    suspend fun saveConnectionConfig(vehicleId: String, host: String, port: Int): Boolean {
        if (!isOwned(vehicleId)) return false
        val trimmedHost = host.trim()
        val existing = dao.findConnection(vehicleId)
        val configChanged =
            existing == null || existing.host != trimmedHost || existing.port != port
        dao.upsertConnection(
            Elm327ConnectionEntity(
                vehicleId = vehicleId,
                host = trimmedHost,
                port = port,
                lastConnectionAtMillis = existing?.lastConnectionAtMillis,
                lastEcuCommunicationAtMillis = existing?.lastEcuCommunicationAtMillis,
                connectionStatus =
                    existing?.connectionStatus
                        ?.takeUnless { configChanged }
                        ?: ConnectionStatus.NOT_CONFIGURED,
            ),
        )
        return true
    }

    /** Records a successful adapter link. It does not imply that the ECU answered. */
    suspend fun recordAdapterConnected(vehicleId: String, atMillis: Long = clock()): Boolean =
        isOwned(vehicleId) &&
            dao.markAdapterConnected(vehicleId, ConnectionStatus.CONNECTED, atMillis) > 0

    /** Records a verified ECU response, updating the last-communication timestamp. */
    suspend fun recordEcuVerified(vehicleId: String, atMillis: Long = clock()): Boolean =
        isOwned(vehicleId) &&
            dao.markEcuVerified(vehicleId, ConnectionStatus.CONNECTED, atMillis) > 0

    suspend fun markDisconnected(vehicleId: String): Boolean =
        isOwned(vehicleId) &&
            dao.updateConnectionStatus(vehicleId, ConnectionStatus.DISCONNECTED) > 0

    suspend fun markError(vehicleId: String): Boolean =
        isOwned(vehicleId) && dao.updateConnectionStatus(vehicleId, ConnectionStatus.ERROR) > 0

    /**
     * Replaces the discovered capability set. Collection preferences are preserved for PIDs that
     * were already known; new PIDs default to enabled. Only call this after a successful discovery
     * so a failure never wipes the previous result.
     */
    suspend fun replaceCapabilities(
        vehicleId: String,
        pids: Set<Int>,
        discoveredAtMillis: Long = clock(),
    ): Boolean {
        if (!isOwned(vehicleId)) return false
        val enabledByPid =
            dao.supportedPids(vehicleId).associate { it.pid to it.enabledForCollection }
        dao.replaceSupportedPids(
            vehicleId,
            buildCapabilityEntities(vehicleId, pids, discoveredAtMillis, enabledByPid),
        )
        return true
    }

    suspend fun setPidEnabled(vehicleId: String, pid: Int, enabled: Boolean): Boolean =
        isOwned(vehicleId) && dao.setPidEnabled(vehicleId, pid, enabled) > 0

    private fun currentOwner(): String? = authSession.signedInUser.value

    private suspend fun isOwned(vehicleId: String): Boolean {
        val owner = currentOwner() ?: return false
        return dao.findVehicle(vehicleId)?.ownerUsername == owner
    }

    private fun Elm327Connection.toEntity(vehicleId: String): Elm327ConnectionEntity =
        Elm327ConnectionEntity(
            vehicleId = vehicleId,
            host = host.trim(),
            port = port,
            lastConnectionAtMillis = lastConnectionAtMillis,
            lastEcuCommunicationAtMillis = lastEcuCommunicationAtMillis,
            connectionStatus = status,
        )
}

/**
 * Builds the capability rows for a successful discovery. PIDs that were already stored keep their
 * collection preference; new ones are enabled by default.
 */
internal fun buildCapabilityEntities(
    vehicleId: String,
    pids: Set<Int>,
    discoveredAtMillis: Long,
    enabledByPid: Map<Int, Boolean>,
): List<SupportedPidEntity> =
    pids.sorted().map { pid ->
        SupportedPidEntity(
            vehicleId = vehicleId,
            pid = pid,
            discoveredAtMillis = discoveredAtMillis,
            enabledForCollection = enabledByPid[pid] ?: true,
        )
    }
