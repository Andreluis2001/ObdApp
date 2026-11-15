package com.app.drivedata.vehicle

import com.app.drivedata.vehicle.model.VehicleIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleIdentityDecoderTest {

    @Test
    fun decodesManufacturerAndSerialNumberFromVin() {
        val identity = VehicleIdentityDecoder.decode("WBA3A5G59DNP26082")

        assertEquals(
            VehicleIdentity(
                vin = "WBA3A5G59DNP26082",
                manufacturer = "BMW",
                serialNumber = "P26082",
            ),
            identity,
        )
    }

    @Test
    fun decodesLowercaseVinWithSurroundingWhitespace() {
        val identity = VehicleIdentityDecoder.decode("  wba3a5g59dnp26082\n")

        assertEquals("WBA3A5G59DNP26082", identity?.vin)
        assertEquals("BMW", identity?.manufacturer)
        assertEquals("P26082", identity?.serialNumber)
    }

    @Test
    fun returnsNullForBlankInput() {
        assertNull(VehicleIdentityDecoder.decode(null))
        assertNull(VehicleIdentityDecoder.decode(""))
        assertNull(VehicleIdentityDecoder.decode("   "))
    }

    @Test
    fun returnsNullForMalformedVin() {
        assertNull(VehicleIdentityDecoder.decode("NOT-A-VIN"))
    }
}
