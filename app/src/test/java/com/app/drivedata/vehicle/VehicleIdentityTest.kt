package com.app.drivedata.vehicle

import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleCapabilities
import com.app.drivedata.vehicle.model.VehicleDetails
import com.app.drivedata.vehicle.model.VehicleIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleIdentityTest {

    @Test
    fun displayName_combinesManufacturerAndSerialNumber() {
        val identity = VehicleIdentity(vin = "WBA3A5G59DNP26082", manufacturer = "BMW", serialNumber = "P26082")

        assertEquals("BMW P26082", identity.displayName)
    }

    @Test
    fun displayName_fallsBackToVinWhenSerialNumberIsUnknown() {
        val identity = VehicleIdentity(vin = "WBA3A5G59DNP26082", manufacturer = "BMW", serialNumber = null)

        assertEquals("BMW WBA3A5G59DNP26082", identity.displayName)
    }

    @Test
    fun displayName_usesSerialNumberWithoutManufacturer() {
        val identity = VehicleIdentity(vin = "WBA3A5G59DNP26082", manufacturer = null, serialNumber = "P26082")

        assertEquals("P26082", identity.displayName)
    }

    @Test
    fun vehicle_displayNameFallsBackToIdentityWhenMakeAndModelAreBlank() {
        val vehicle = vehicle(make = "", model = "")

        assertEquals("BMW P26082", vehicle.displayName)
    }

    @Test
    fun vehicle_displayNameUsesIdentityWhenOnlyTheMakeWasStored() {
        val vehicle = vehicle(make = "BMW", model = "")

        assertEquals("BMW P26082", vehicle.displayName)
    }

    @Test
    fun vehicle_displayNameKeepsLegacyMakeAndModel() {
        val vehicle = vehicle(make = "Chevrolet", model = "S10", identity = null)

        assertEquals("Chevrolet S10", vehicle.displayName)
    }

    private fun vehicle(
        make: String,
        model: String,
        identity: VehicleIdentity? =
            VehicleIdentity(vin = "WBA3A5G59DNP26082", manufacturer = "BMW", serialNumber = "P26082"),
    ): Vehicle =
        Vehicle(
            id = "v1",
            ownerUsername = "admin",
            details = VehicleDetails(make = make, model = model),
            identity = identity,
            connection = Elm327Connection.unconfigured(),
            capabilities = VehicleCapabilities(),
            isActive = true,
            createdAtMillis = 1,
            updatedAtMillis = 1,
        )
}
