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

data class MetricDefinition(
    val id: MetricId,
    val unit: String,
    val commandFactory: () -> ObdCommand,
)

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
