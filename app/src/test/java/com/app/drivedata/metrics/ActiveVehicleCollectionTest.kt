package com.app.drivedata.metrics

import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.SupportedPid
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleCapabilities
import com.app.drivedata.vehicle.model.VehicleDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveVehicleCollectionTest {

    @Test
    fun mapsOnlyEnabledCatalogPids() {
        val collection =
            vehicle(
                capabilities =
                    VehicleCapabilities(
                        supportedPids =
                            listOf(
                                SupportedPid(0x0C, enabledForCollection = true),
                                SupportedPid(0x0D, enabledForCollection = false),
                                SupportedPid(0x99, enabledForCollection = true), // outside catalog
                            ),
                        lastDiscoveredAtMillis = 10,
                    ),
            ).toActiveVehicleCollection()

        assertEquals(listOf(MetricId.RPM), collection.catalog.map { it.id })
        assertTrue(collection.hasDiscovery)
        assertTrue(collection.canCollect)
    }

    @Test
    fun hasNoCatalogWithoutDiscovery() {
        val collection = vehicle().toActiveVehicleCollection()

        assertFalse(collection.hasDiscovery)
        assertTrue(collection.catalog.isEmpty())
        assertFalse(collection.canCollect)
    }

    @Test
    fun cannotCollectWhenEveryPidIsDisabled() {
        val collection =
            vehicle(
                capabilities =
                    VehicleCapabilities(
                        supportedPids = listOf(SupportedPid(0x0C, enabledForCollection = false)),
                        lastDiscoveredAtMillis = 10,
                    ),
            ).toActiveVehicleCollection()

        assertTrue(collection.hasDiscovery)
        assertTrue(collection.catalog.isEmpty())
        assertFalse(collection.canCollect)
    }

    @Test
    fun matchesOnlyTheVehicleAdapter() {
        val collection = vehicle().toActiveVehicleCollection()

        assertTrue(collection.matches(ObdWifiConfig(HOST, PORT)))
        assertTrue(collection.matches(ObdWifiConfig("  $HOST  ", PORT)))
        assertFalse(collection.matches(ObdWifiConfig(HOST, PORT + 1)))
        assertFalse(collection.matches(ObdWifiConfig("192.168.0.99", PORT)))
    }

    private fun vehicle(
        capabilities: VehicleCapabilities = VehicleCapabilities(),
    ): Vehicle =
        Vehicle(
            id = "vehicle-1",
            ownerUsername = "admin",
            details = VehicleDetails(make = "Chevrolet", model = "S10"),
            connection = Elm327Connection(host = HOST, port = PORT),
            capabilities = capabilities,
            isActive = true,
            createdAtMillis = 1,
            updatedAtMillis = 1,
        )

    private companion object {
        const val HOST = "192.168.0.10"
        const val PORT = 35_000
    }
}
