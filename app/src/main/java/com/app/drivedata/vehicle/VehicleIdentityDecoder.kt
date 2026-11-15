package com.app.drivedata.vehicle

import com.app.drivedata.logging.AppLog
import com.app.drivedata.vehicle.model.VehicleIdentity
import io.github.kabirnayeem99.viminfo.VinInfo

/**
 * Decodes the raw VIN reported by the ECU into the vehicle manufacturer and serial number.
 *
 * Returns `null` when the value is blank or when kvininfo rejects it. A missing VIN is expected on
 * some vehicles and must never block adapter pairing.
 */
object VehicleIdentityDecoder {

    fun decode(rawVin: String?): VehicleIdentity? {
        val trimmed = rawVin?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            VinInfo.fromNumber(trimmed).getOrNull()?.use { vin ->
                VehicleIdentity(
                    vin = vin.vinNumber,
                    manufacturer = vin.manufacturer?.trim()?.ifBlank { null },
                    serialNumber = vin.serialNumber.trim().ifBlank { null },
                )
            }
        } catch (error: Exception) {
            AppLog.w("VIN '$trimmed' could not be decoded", error)
            null
        }
    }
}
