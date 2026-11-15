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
    /**
     * Name shown across the app.
     *
     * VIN-discovered vehicles have no model, so their name comes from the decoded identity
     * (manufacturer + serial, falling back to the VIN). Legacy vehicles with a user-provided model
     * keep the name that was entered.
     */
    val displayName: String
        get() =
            when {
                details.model.isNotBlank() -> details.displayName
                identity != null -> identity.displayName
                else -> details.displayName.ifBlank { details.licensePlate.orEmpty() }
            }
}
