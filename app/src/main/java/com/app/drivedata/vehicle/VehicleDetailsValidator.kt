package com.app.drivedata.vehicle

import com.app.drivedata.vehicle.model.VehicleDetails
import java.util.Calendar

/** Raw form values, before trimming and normalization. */
data class VehicleDetailsDraft(
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val licensePlate: String = "",
    val nickname: String = "",
)

data class VehicleDetailsErrors(
    val make: Boolean = false,
    val model: Boolean = false,
    val year: Boolean = false,
    val licensePlate: Boolean = false,
    val nickname: Boolean = false,
) {
    val hasErrors: Boolean
        get() = make || model || year || licensePlate || nickname
}

sealed interface VehicleDetailsValidation {
    data class Valid(val details: VehicleDetails) : VehicleDetailsValidation

    data class Invalid(val errors: VehicleDetailsErrors) : VehicleDetailsValidation
}

/** Validates and normalizes the vehicle information form. */
object VehicleDetailsValidator {

    const val MIN_YEAR = 1886
    const val MAX_NAME_LENGTH = 60
    const val MAX_NICKNAME_LENGTH = 40

    private val PLATE_PATTERN = Regex("[A-Z0-9]{6,8}")

    fun validate(
        draft: VehicleDetailsDraft,
        maxYear: Int = currentYear() + 1,
    ): VehicleDetailsValidation {
        val make = draft.make.trim()
        val model = draft.model.trim()
        val nickname = draft.nickname.trim()
        val yearText = draft.year.trim()
        val licensePlate = normalizePlate(draft.licensePlate)

        val year = yearText.toIntOrNull()
        val yearInvalid = yearText.isNotEmpty() && (year == null || year !in MIN_YEAR..maxYear)
        val plateInvalid = licensePlate.isNotEmpty() && !PLATE_PATTERN.matches(licensePlate)

        val errors =
            VehicleDetailsErrors(
                make = make.isEmpty() || make.length > MAX_NAME_LENGTH,
                model = model.isEmpty() || model.length > MAX_NAME_LENGTH,
                year = yearInvalid,
                licensePlate = plateInvalid,
                nickname = nickname.length > MAX_NICKNAME_LENGTH,
            )
        if (errors.hasErrors) return VehicleDetailsValidation.Invalid(errors)

        return VehicleDetailsValidation.Valid(
            VehicleDetails(
                make = make,
                model = model,
                year = year,
                licensePlate = licensePlate.ifEmpty { null },
                nickname = nickname.ifEmpty { null },
            ),
        )
    }

    /** Uppercases and removes separators so "abc-1234" becomes "ABC1234". */
    fun normalizePlate(value: String): String =
        value.trim().uppercase().filterNot { it.isWhitespace() || it == '-' }

    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
}
