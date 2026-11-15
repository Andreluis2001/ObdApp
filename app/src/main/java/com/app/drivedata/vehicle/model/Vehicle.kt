package com.app.drivedata.vehicle.model

data class VehicleDetails(
    val make: String,
    val model: String,
    val licensePlate: String? = null,
) {
    val displayName: String
        get() = "$make $model".trim()
}

data class Vehicle(
    val id: String,
    val ownerUsername: String,
    val details: VehicleDetails,
    val identity: VehicleIdentity? = null,
    val connection: Elm327Connection,
    val capabilities: VehicleCapabilities,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val displayName: String
        get() = details.displayName
}
