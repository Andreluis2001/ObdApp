package com.app.drivedata.vehicle.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.drivedata.metrics.dao.DriveDataDatabase
import com.app.drivedata.vehicle.entity.Elm327ConnectionEntity
import com.app.drivedata.vehicle.entity.SupportedPidEntity
import com.app.drivedata.vehicle.entity.VehicleEntity
import com.app.drivedata.vehicle.model.ConnectionStatus
import com.app.drivedata.vehicle.model.VehicleIdentity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleDaoTest {

    private lateinit var database: DriveDataDatabase
    private lateinit var dao: VehicleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, DriveDataDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.vehicleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertVehicle_withConnection_persistsBoth() =
        runBlocking {
            dao.insertVehicle(vehicle("v1"), connection("v1"))

            assertEquals("admin", dao.findVehicle("v1")?.ownerUsername)
            assertEquals("192.168.0.10", dao.findConnection("v1")?.host)
        }

    @Test
    fun setActive_keepsASingleActiveVehiclePerOwner() =
        runBlocking {
            dao.insertVehicle(vehicle("v1"), null)
            dao.insertVehicle(vehicle("v2"), null)
            dao.insertVehicle(vehicle("v3", owner = "other"), null)

            assertTrue(dao.setActive("admin", "v1"))
            assertTrue(dao.findVehicle("v1")!!.isActive)
            assertFalse(dao.findVehicle("v2")!!.isActive)

            assertTrue(dao.setActive("admin", "v2"))
            assertFalse(dao.findVehicle("v1")!!.isActive)
            assertTrue(dao.findVehicle("v2")!!.isActive)
            assertEquals(1, dao.countActiveVehicles("admin"))
        }

    @Test
    fun setActive_rejectsAnotherOwnersVehicleWithoutClearingTheCurrentOne() =
        runBlocking {
            dao.insertVehicle(vehicle("v1"), null)
            dao.insertVehicle(vehicle("v2", owner = "other"), null)
            dao.setActive("admin", "v1")

            assertFalse(dao.setActive("admin", "v2"))
            assertTrue(dao.findVehicle("v1")!!.isActive)
            assertEquals(1, dao.countActiveVehicles("admin"))
        }

    @Test
    fun deleteVehicle_cascadesToConnectionAndSupportedPids() =
        runBlocking {
            dao.insertVehicle(vehicle("v1"), connection("v1"))
            dao.insertSupportedPids(listOf(pid("v1", 0x0C), pid("v1", 0x0D)))

            assertEquals(1, dao.deleteVehicle("v1", "admin"))

            assertNull(dao.findVehicle("v1"))
            assertNull(dao.findConnection("v1"))
            assertTrue(dao.supportedPids("v1").isEmpty())
        }

    @Test
    fun deleteVehicle_doesNotDeleteAnotherOwnersVehicle() =
        runBlocking {
            dao.insertVehicle(vehicle("v1", owner = "other"), null)

            assertEquals(0, dao.deleteVehicle("v1", "admin"))
            assertEquals("other", dao.findVehicle("v1")?.ownerUsername)
        }

    @Test
    fun replaceSupportedPids_replacesTheWholeSet() =
        runBlocking {
            dao.insertVehicle(vehicle("v1"), null)
            dao.insertSupportedPids(listOf(pid("v1", 0x0C), pid("v1", 0x0D)))

            dao.replaceSupportedPids(
                "v1",
                listOf(
                    pid("v1", 0x0C, enabled = false, atMillis = 20),
                    pid("v1", 0x05, atMillis = 20),
                ),
            )

            val byPid = dao.supportedPids("v1").associateBy { it.pid }
            assertEquals(setOf(0x05, 0x0C), byPid.keys)
            assertFalse(byPid.getValue(0x0C).enabledForCollection)
            assertTrue(byPid.getValue(0x05).enabledForCollection)
            assertTrue(byPid.values.all { it.discoveredAtMillis == 20L })
        }

    @Test
    fun insertVehicle_withIdentity_persistsDecodedVinFields() =
        runBlocking {
            val identity =
                VehicleIdentity(
                    vin = "WBA3A5G59DNP26082",
                    manufacturer = "BMW",
                    serialNumber = "P26082",
                )
            dao.insertVehicle(vehicle("v1", identity = identity), null)

            val stored = dao.findVehicle("v1")
            assertEquals("WBA3A5G59DNP26082", stored?.vin)
            assertEquals("BMW", stored?.manufacturer)
            assertEquals("P26082", stored?.serialNumber)
        }

    private fun vehicle(
        id: String,
        owner: String = "admin",
        isActive: Boolean = false,
        identity: VehicleIdentity? = null,
    ): VehicleEntity =
        VehicleEntity(
            id = id,
            ownerUsername = owner,
            make = "Chevrolet",
            model = "S10",
            licensePlate = null,
            vin = identity?.vin,
            manufacturer = identity?.manufacturer,
            serialNumber = identity?.serialNumber,
            isActive = isActive,
            createdAtMillis = 1,
            updatedAtMillis = 1,
        )

    private fun connection(vehicleId: String): Elm327ConnectionEntity =
        Elm327ConnectionEntity(
            vehicleId = vehicleId,
            host = "192.168.0.10",
            port = 35_000,
            connectionStatus = ConnectionStatus.NOT_CONFIGURED,
        )

    private fun pid(
        vehicleId: String,
        pid: Int,
        enabled: Boolean = true,
        atMillis: Long = 1,
    ): SupportedPidEntity =
        SupportedPidEntity(
            vehicleId = vehicleId,
            pid = pid,
            discoveredAtMillis = atMillis,
            enabledForCollection = enabled,
        )
}
