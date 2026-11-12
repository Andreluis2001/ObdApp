package com.app.drivedata.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDetailsValidatorTest {

    private val maxYear = 2027

    @Test
    fun validate_normalizesTheOptionalFields() {
        val result =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(
                    make = "  Chevrolet ",
                    model = " S10 ",
                    year = " 2020 ",
                    licensePlate = " abc-1d23 ",
                    nickname = "  Minha caminhonete ",
                ),
                maxYear = maxYear,
            )

        val details = (result as VehicleDetailsValidation.Valid).details
        assertEquals("Chevrolet", details.make)
        assertEquals("S10", details.model)
        assertEquals(2020, details.year)
        assertEquals("ABC1D23", details.licensePlate)
        assertEquals("Minha caminhonete", details.nickname)
    }

    @Test
    fun validate_keepsBlankOptionalFieldsNull() {
        val result =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(make = "Fiat", model = "Argo"),
                maxYear = maxYear,
            )

        val details = (result as VehicleDetailsValidation.Valid).details
        assertNull(details.year)
        assertNull(details.licensePlate)
        assertNull(details.nickname)
        assertEquals("Fiat Argo", details.displayName)
    }

    @Test
    fun validate_requiresMakeAndModel() {
        val result =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(make = "   ", model = ""),
                maxYear = maxYear,
            )

        val errors = (result as VehicleDetailsValidation.Invalid).errors
        assertTrue(errors.make)
        assertTrue(errors.model)
        assertFalse(errors.year)
        assertFalse(errors.licensePlate)
        assertFalse(errors.nickname)
    }

    @Test
    fun validate_rejectsAYearOutsideTheAllowedRange() {
        listOf("1885", "2028", "abc", "0").forEach { year ->
            val result =
                VehicleDetailsValidator.validate(
                    VehicleDetailsDraft(make = "Fiat", model = "Argo", year = year),
                    maxYear = maxYear,
                )
            assertTrue("expected '$year' to be invalid", result is VehicleDetailsValidation.Invalid)
            assertTrue((result as VehicleDetailsValidation.Invalid).errors.year)
        }

        val boundaryResult =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(make = "Fiat", model = "Argo", year = "2027"),
                maxYear = maxYear,
            )
        assertEquals(2027, (boundaryResult as VehicleDetailsValidation.Valid).details.year)
    }

    @Test
    fun validate_rejectsMalformedLicensePlates() {
        listOf("AB12", "ABC123456", "ABC 123!").forEach { plate ->
            val result =
                VehicleDetailsValidator.validate(
                    VehicleDetailsDraft(make = "Fiat", model = "Argo", licensePlate = plate),
                    maxYear = maxYear,
                )
            assertTrue("expected '$plate' to be invalid", result is VehicleDetailsValidation.Invalid)
            assertTrue((result as VehicleDetailsValidation.Invalid).errors.licensePlate)
        }

        val normalized =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(make = "Fiat", model = "Argo", licensePlate = "ABC 1234"),
                maxYear = maxYear,
            )
        assertEquals(
            "ABC1234",
            (normalized as VehicleDetailsValidation.Valid).details.licensePlate,
        )
    }

    @Test
    fun validate_rejectsValuesAboveTheLengthLimits() {
        val longName = "a".repeat(VehicleDetailsValidator.MAX_NAME_LENGTH + 1)
        val longNickname = "b".repeat(VehicleDetailsValidator.MAX_NICKNAME_LENGTH + 1)

        val result =
            VehicleDetailsValidator.validate(
                VehicleDetailsDraft(make = longName, model = longName, nickname = longNickname),
                maxYear = maxYear,
            )

        val errors = (result as VehicleDetailsValidation.Invalid).errors
        assertTrue(errors.make)
        assertTrue(errors.model)
        assertTrue(errors.nickname)
    }

    @Test
    fun normalizePlate_removesSeparatorsAndUppercases() {
        assertEquals("ABC1D23", VehicleDetailsValidator.normalizePlate(" abc-1d23 "))
        assertEquals("ABC1234", VehicleDetailsValidator.normalizePlate("ABC 1234"))
    }
}
