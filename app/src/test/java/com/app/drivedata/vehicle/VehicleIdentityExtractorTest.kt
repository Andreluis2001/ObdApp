package com.app.drivedata.vehicle

import com.app.drivedata.vehicle.model.VehicleIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class VehicleIdentityExtractorTest {

    @Test
    fun readsAndDecodesTheRawVin() =
        runTest {
            val extractor =
                VehicleIdentityExtractor(
                    readRawVin = { "WBA3A5G59DNP26082" },
                    decode = { raw ->
                        VehicleIdentity(
                            vin = raw.orEmpty(),
                            manufacturer = "BMW",
                            serialNumber = "P26082",
                        )
                    },
                )

            assertEquals("WBA3A5G59DNP26082", extractor.read()?.vin)
        }

    @Test
    fun returnsNullWhenTheAdapterDoesNotAnswer() =
        runTest {
            val extractor =
                VehicleIdentityExtractor(
                    readRawVin = { throw IOException("adapter disconnected") },
                )

            assertNull(extractor.read())
        }

    @Test
    fun returnsNullWhenTheVinCannotBeDecoded() =
        runTest {
            val extractor =
                VehicleIdentityExtractor(
                    readRawVin = { "NOT-A-VIN" },
                    decode = { null },
                )

            assertNull(extractor.read())
        }
}
