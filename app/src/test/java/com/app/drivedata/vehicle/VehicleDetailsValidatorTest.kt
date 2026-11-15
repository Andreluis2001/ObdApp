package com.app.drivedata.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDetailsValidatorTest {

    @Test
    fun validate_trimsTheNameFieldsAndNormalizesThePlate() {
        val result =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(
                    make = "  Chevrolet ",
                    model = " S10 ",
                    licensePlate = " abc-1d23 ",
                ),
            )

        val details = (result as VehicleDetailsValidation.Valid).details
        assertEquals("Chevrolet", details.make)
        assertEquals("S10", details.model)
        assertEquals("ABC1D23", details.licensePlate)
        assertEquals("Chevrolet S10", details.displayName)
    }

    @Test
    fun validate_requiresMakeModelAndPlate() {
        val result =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(make = "   ", model = ""),
            )

        val errors = (result as VehicleDetailsValidation.Invalid).errors
        assertTrue(errors.make)
        assertTrue(errors.model)
        assertTrue(errors.licensePlate)
    }

    @Test
    fun validate_acceptsLegacyAndMercosulPlates() {
        listOf("ABC1234", "RDE2R45", "abc-1d23").forEach { plate ->
            val result =
                VehicleDetailsValidator.validate(
                    VehicleDetailsDraft(make = "Fiat", model = "Argo", licensePlate = plate),
                )
            assertTrue("expected '$plate' to be valid", result is VehicleDetailsValidation.Valid)
            assertEquals(
                plate.uppercase().filter(Char::isLetterOrDigit),
                (result as VehicleDetailsValidation.Valid).details.licensePlate,
            )
        }
    }

    @Test
    fun validate_rejectsMalformedOrMissingPlates() {
        listOf("", "AB12", "ABC12345", "ABC123456", "ABC 123!", "1ABC234", "ABCD123").forEach { plate ->
            val result =
                VehicleDetailsValidator.validate(
                    VehicleDetailsDraft(make = "Fiat", model = "Argo", licensePlate = plate),
                )
            assertTrue("expected '$plate' to be invalid", result is VehicleDetailsValidation.Invalid)
            assertTrue((result as VehicleDetailsValidation.Invalid).errors.licensePlate)
        }
    }

    @Test
    fun validate_rejectsValuesAboveTheLengthLimits() {
        val longName = "a".repeat(VehicleDetailsValidator.MAX_NAME_LENGTH + 1)

        val result =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(make = longName, model = longName),
            )

        val errors = (result as VehicleDetailsValidation.Invalid).errors
        assertTrue(errors.make)
        assertTrue(errors.model)
    }

    @Test
    fun normalizePlate_stripsSeparatorsAndUppercases() {
        assertEquals("ABC1D23", VehicleDetailsValidator.normalizePlate(" abc-1d23 "))
        assertEquals("ABC1234", VehicleDetailsValidator.normalizePlate("ABC 1234"))
        assertEquals("ABC1234", VehicleDetailsValidator.normalizePlate("ABC-1234"))
    }
}
