package com.app.drivedata.metrics.model

enum class MetricId {
    RPM,
    SPEED,
    COOLANT_TEMPERATURE,
    ENGINE_LOAD,
    THROTTLE_POSITION,
    INTAKE_MANIFOLD_PRESSURE,
    MAF,
    FUEL_LEVEL,
    ;

    /** Stable snake_case name used by the backend contract. */
    val wireName: String
        get() = name.lowercase()
}
