package com.app.drivedata.ui.connection

import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.SupportedPid
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleCapabilities
import com.app.drivedata.vehicle.model.VehicleDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPidsSummaryTest {

    @Test
    fun noActiveVehicle_producesAnEmptySummary() {
        val summary = (null as Vehicle?).toSupportedPidsSummary()

        assertNull(summary.vehicleName)
        assertNull(summary.discoveredAtMillis)
        assertTrue(summary.pids.isEmpty())
    }

    @Test
    fun vehicleWithoutDiscovery_producesNameAndNoPids() {
        val summary =
            vehicle(
                pids = emptyList(),
                discoveredAtMillis = null,
            ).toSupportedPidsSummary()

        assertEquals("Fiat Uno", summary.vehicleName)
        assertNull(summary.discoveredAtMillis)
        assertTrue(summary.pids.isEmpty())
    }

    @Test
    fun discovery_keepsOnlyCatalogPidsInCatalogOrder() {
        val summary =
            vehicle(
                pids =
                    listOf(
                        SupportedPid(pid = 0x0D, enabledForCollection = false),
                        SupportedPid(pid = 0x42),
                        SupportedPid(pid = 0x0C),
                    ),
                discoveredAtMillis = 1_000L,
            ).toSupportedPidsSummary()

        assertEquals("Fiat Uno", summary.vehicleName)
        assertEquals(1_000L, summary.discoveredAtMillis)
        assertEquals(listOf(0x0C, 0x0D), summary.pids.map { it.definition.pid })
        assertTrue(summary.pids.first { it.definition.pid == 0x0C }.enabledForCollection)
        assertFalse(summary.pids.first { it.definition.pid == 0x0D }.enabledForCollection)
    }

    @Test
    fun discoveryWithoutCatalogPids_producesTimestampAndEmptyList() {
        val summary =
            vehicle(
                pids = listOf(SupportedPid(pid = 0x42)),
                discoveredAtMillis = 2_000L,
            ).toSupportedPidsSummary()

        assertEquals(2_000L, summary.discoveredAtMillis)
        assertTrue(summary.pids.isEmpty())
    }

    private fun vehicle(
        pids: List<SupportedPid>,
        discoveredAtMillis: Long?,
    ): Vehicle =
        Vehicle(
            id = "vehicle-1",
            ownerUsername = "user",
            details = VehicleDetails(make = "Fiat", model = "Uno"),
            connection = Elm327Connection(host = "192.168.0.10", port = 35_000),
            capabilities =
                VehicleCapabilities(
                    supportedPids = pids,
                    lastDiscoveredAtMillis = discoveredAtMillis,
                ),
            isActive = true,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
}
