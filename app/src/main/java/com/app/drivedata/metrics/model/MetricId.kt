package com.app.drivedata.metrics.model

/**
 * Numeric OBD-II parameters collected on each sequential sweep.
 *
 * Display names live in Android string resources; this enum only identifies the parameter.
 */
enum class MetricId {
    RPM,
    SPEED,
    COOLANT_TEMPERATURE,
    ENGINE_LOAD,
    THROTTLE_POSITION,
    INTAKE_MANIFOLD_PRESSURE,
    MAF,
    FUEL_LEVEL,
}
