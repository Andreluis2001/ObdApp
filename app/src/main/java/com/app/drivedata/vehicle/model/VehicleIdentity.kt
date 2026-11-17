package com.app.drivedata.vehicle.model

/**
 * Vehicle data decoded from the VIN reported by the ECU.
 *
 * [manufacturer] and [serialNumber] are best-effort WMI/VIS decodes and can be unknown even when
 * the VIN itself was read successfully.
 */
data class VehicleIdentity(
    val vin: String,
    val manufacturer: String?,
    val serialNumber: String?,
) {
    /**
     * Human-readable name built from the decoded values. The serial number identifies a single
     * vehicle, while the VIN is used when the serial number could not be decoded.
     */
    val displayName: String
        get() = listOfNotNull(manufacturer, serialNumber ?: vin).joinToString(" ").trim()
}
