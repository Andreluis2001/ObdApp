package com.app.drivedata.metrics

import com.app.drivedata.metrics.model.MetricId
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.engine.LoadCommand
import com.github.eltonvs.obd.command.engine.MassAirFlowCommand
import com.github.eltonvs.obd.command.engine.RPMCommand
import com.github.eltonvs.obd.command.engine.SpeedCommand
import com.github.eltonvs.obd.command.engine.ThrottlePositionCommand
import com.github.eltonvs.obd.command.fuel.FuelLevelCommand
import com.github.eltonvs.obd.command.pressure.IntakeManifoldPressureCommand
import com.github.eltonvs.obd.command.temperature.EngineCoolantTemperatureCommand

/**
 * A collectable parameter and the library command used to read it.
 *
 * [unit] is the display unit reported by the corresponding library command; only used for
 * parameters that are available, since unavailable readings show no value.
 */
data class MetricDefinition(
    val id: MetricId,
    val unit: String,
    val commandFactory: () -> ObdCommand,
)

/**
 * Common Mode 01 parameters, polled sequentially in this order.
 *
 * Only commands that actually exist in the pinned library version (`kotlin-obd-api:1.4.1`) are
 * listed here.
 */
val defaultMetricCatalog: List<MetricDefinition> =
    listOf(
        MetricDefinition(MetricId.RPM, "RPM") { RPMCommand() },
        MetricDefinition(MetricId.SPEED, "km/h") { SpeedCommand() },
        MetricDefinition(MetricId.COOLANT_TEMPERATURE, "\u00B0C") { EngineCoolantTemperatureCommand() },
        MetricDefinition(MetricId.ENGINE_LOAD, "%") { LoadCommand() },
        MetricDefinition(MetricId.THROTTLE_POSITION, "%") { ThrottlePositionCommand() },
        MetricDefinition(MetricId.INTAKE_MANIFOLD_PRESSURE, "kPa") { IntakeManifoldPressureCommand() },
        MetricDefinition(MetricId.MAF, "g/s") { MassAirFlowCommand() },
        MetricDefinition(MetricId.FUEL_LEVEL, "%") { FuelLevelCommand() },
    )
