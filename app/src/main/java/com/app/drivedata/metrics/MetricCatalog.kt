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
    /** OBD PID byte advertised by the supported-PIDs bitmap (e.g. 0x0C for RPM). */
    val pid: Int,
    val unit: String,
    val commandFactory: () -> ObdCommand,
)

val defaultMetricCatalog: List<MetricDefinition> =
    listOf(
        MetricDefinition(MetricId.RPM, 0x0C, "RPM") { RPMCommand() },
        MetricDefinition(MetricId.SPEED, 0x0D, "km/h") { SpeedCommand() },
        MetricDefinition(MetricId.COOLANT_TEMPERATURE, 0x05, "\u00B0C") { EngineCoolantTemperatureCommand() },
        MetricDefinition(MetricId.ENGINE_LOAD, 0x04, "%") { LoadCommand() },
        MetricDefinition(MetricId.THROTTLE_POSITION, 0x11, "%") { ThrottlePositionCommand() },
        MetricDefinition(MetricId.INTAKE_MANIFOLD_PRESSURE, 0x0B, "kPa") { IntakeManifoldPressureCommand() },
        MetricDefinition(MetricId.MAF, 0x10, "g/s") { MassAirFlowCommand() },
        MetricDefinition(MetricId.FUEL_LEVEL, 0x2F, "%") { FuelLevelCommand() },
    )
