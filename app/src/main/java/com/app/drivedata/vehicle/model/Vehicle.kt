package com.app.drivedata.vehicle.model

data class VehicleDetails(
    val make: String,
    val model: String,
    val year: Int? = null,
    val licensePlate: String? = null,
    val nickname: String? = null,
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: "$make $model".trim()
}

data class Vehicle(
    val id: String,
    val ownerUsername: String,
    val details: VehicleDetails,
    val connection: Elm327Connection,
    val capabilities: VehicleCapabilities,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val displayName: String
        get() = details.displayName
}
