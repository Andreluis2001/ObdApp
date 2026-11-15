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
)
