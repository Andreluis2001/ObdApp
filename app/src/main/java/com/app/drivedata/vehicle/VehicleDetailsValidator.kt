package com.app.drivedata.vehicle

import com.app.drivedata.vehicle.model.VehicleDetails

/** Raw form values, before trimming and normalization. */
data class VehicleDetailsDraft(
    val make: String = "",
    val model: String = "",
    val licensePlate: String = "",
)

data class VehicleDetailsErrors(
    val make: Boolean = false,
    val model: Boolean = false,
    val licensePlate: Boolean = false,
) {
    val hasErrors: Boolean
        get() = make || model || licensePlate
}

sealed interface VehicleDetailsValidation {
    data class Valid(val details: VehicleDetails) : VehicleDetailsValidation

    data class Invalid(val errors: VehicleDetailsErrors) : VehicleDetailsValidation
}

/** Validates and normalizes the vehicle information form. */
object VehicleDetailsValidator {

    const val MAX_NAME_LENGTH = 60

    const val MAX_PLATE_LENGTH = 7

    /** Legacy format: three letters followed by four digits, e.g. "ABC1234". */
    private val LEGACY_PLATE_PATTERN = Regex("[A-Z]{3}[0-9]{4}")

    /** Mercosul format: three letters, digit, letter, two digits, e.g. "RDE2R45". */
    private val MERCOSUL_PLATE_PATTERN = Regex("[A-Z]{3}[0-9][A-Z][0-9]{2}")

    fun validate(draft: VehicleDetailsDraft): VehicleDetailsValidation {
        val make = draft.make.trim()
        val model = draft.model.trim()
        val licensePlate = normalizePlate(draft.licensePlate)

        val plateInvalid =
            licensePlate.isEmpty() ||
                (!LEGACY_PLATE_PATTERN.matches(licensePlate) &&
                    !MERCOSUL_PLATE_PATTERN.matches(licensePlate))

        val errors =
            VehicleDetailsErrors(
                make = make.isEmpty() || make.length > MAX_NAME_LENGTH,
                model = model.isEmpty() || model.length > MAX_NAME_LENGTH,
                licensePlate = plateInvalid,
            )
        if (errors.hasErrors) return VehicleDetailsValidation.Invalid(errors)

        return VehicleDetailsValidation.Valid(
            VehicleDetails(
                make = make,
                model = model,
                licensePlate = licensePlate,
            ),
        )
    }

    /** Uppercases and keeps letters/digits only so "abc-1234" becomes "ABC1234". */
    fun normalizePlate(value: String): String =
        value.trim().uppercase().filter { it.isLetterOrDigit() }
}
