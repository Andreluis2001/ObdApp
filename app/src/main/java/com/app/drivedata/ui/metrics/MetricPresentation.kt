package com.app.drivedata.ui.metrics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.app.drivedata.R
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeOrange
import com.app.drivedata.ui.theme.HomePurple

/**
 * Visual metadata for a live parameter. The min/max values are the usual OBD-II measuring ranges
 * shown under each value; they are not vehicle-specific limits.
 */
internal data class MetricPresentation(
    val labelRes: Int,
    val unitRes: Int,
    val icon: ImageVector,
    val tint: Color,
    val range: ClosedFloatingPointRange<Double>,
)

/** Order matches the "Parâmetros do Veículo" mockup, with the remaining catalog entries after it. */
internal val parameterOrder: List<MetricId> =
    listOf(
        MetricId.RPM,
        MetricId.SPEED,
        MetricId.COOLANT_TEMPERATURE,
        MetricId.ENGINE_LOAD,
        MetricId.INTAKE_MANIFOLD_PRESSURE,
        MetricId.THROTTLE_POSITION,
        MetricId.MAF,
        MetricId.FUEL_LEVEL,
    )

internal fun MetricId.presentation(): MetricPresentation =
    when (this) {
        MetricId.RPM ->
            MetricPresentation(
                labelRes = R.string.metric_rpm,
                unitRes = R.string.home_unit_rpm,
                icon = Icons.Filled.Speed,
                tint = HomeGreen,
                range = 0.0..7_000.0,
            )

        MetricId.SPEED ->
            MetricPresentation(
                labelRes = R.string.metric_speed,
                unitRes = R.string.home_unit_speed,
                icon = Icons.Filled.DirectionsCar,
                tint = HomeBlue,
                range = 0.0..180.0,
            )

        MetricId.COOLANT_TEMPERATURE ->
            MetricPresentation(
                labelRes = R.string.metric_coolant_temperature,
                unitRes = R.string.home_unit_temperature,
                icon = Icons.Filled.Thermostat,
                tint = HomeOrange,
                range = 40.0..120.0,
            )

        MetricId.ENGINE_LOAD ->
            MetricPresentation(
                labelRes = R.string.metric_engine_load,
                unitRes = R.string.home_unit_percent,
                icon = Icons.Filled.Bolt,
                tint = HomePurple,
                range = 0.0..100.0,
            )

        MetricId.INTAKE_MANIFOLD_PRESSURE ->
            MetricPresentation(
                labelRes = R.string.metric_intake_manifold_pressure,
                unitRes = R.string.metrics_unit_pressure,
                icon = Icons.Filled.Compress,
                tint = HomeGreen,
                range = 20.0..105.0,
            )

        MetricId.THROTTLE_POSITION ->
            MetricPresentation(
                labelRes = R.string.metric_throttle_position,
                unitRes = R.string.home_unit_percent,
                icon = Icons.Filled.Tune,
                tint = HomePurple,
                range = 0.0..100.0,
            )

        MetricId.MAF ->
            MetricPresentation(
                labelRes = R.string.metric_maf,
                unitRes = R.string.metrics_unit_airflow,
                icon = Icons.Filled.Air,
                tint = HomeBlue,
                range = 0.0..150.0,
            )

        MetricId.FUEL_LEVEL ->
            MetricPresentation(
                labelRes = R.string.metric_fuel_level,
                unitRes = R.string.home_unit_percent,
                icon = Icons.Filled.LocalGasStation,
                tint = HomeOrange,
                range = 0.0..100.0,
            )
    }
