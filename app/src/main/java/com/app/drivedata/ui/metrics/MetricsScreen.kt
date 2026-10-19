package com.app.drivedata.ui.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import java.util.Locale

@Composable
fun MetricsScreen(
    viewModel: MetricsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MetricsContent(
        state = state,
        onStart = viewModel::startCollection,
        onStop = viewModel::stopCollection,
        modifier = modifier,
    )
}

@Composable
private fun MetricsContent(
    state: MetricsUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canCollect =
        state.connection is ObdConnectionState.Connected &&
            (state.connection as ObdConnectionState.Connected).ecuStatus == EcuStatus.VERIFIED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.metrics_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStart, enabled = canCollect && !state.isCollecting) {
                Text(stringResource(R.string.metrics_start))
            }
            OutlinedButton(onClick = onStop, enabled = state.isCollecting) {
                Text(stringResource(R.string.metrics_stop))
            }
        }

        val snapshot = state.snapshot
        if (snapshot == null) {
            Text(text = stringResource(R.string.metrics_idle))
        } else {
            SnapshotCard(snapshot = snapshot)
        }
    }
}

@Composable
private fun SnapshotCard(snapshot: MetricsSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            snapshot.vin?.let { vin ->
                Text(
                    text = stringResource(R.string.metrics_vin, vin),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            snapshot.readings.forEach { reading ->
                ReadingRow(reading = reading)
            }
        }
    }
}

@Composable
private fun ReadingRow(reading: MetricReading) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(reading.id.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = reading.displayText(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MetricReading.displayText(): String =
    when (val current = value) {
        is ReadingValue.Available -> "${formatValue(current.value)} $unit"
        ReadingValue.Unavailable -> stringResource(R.string.metrics_unavailable)
    }

/** Renders whole numbers without a decimal part (e.g. RPM, speed) and keeps one decimal otherwise. */
private fun formatValue(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }

private fun MetricId.labelRes(): Int =
    when (this) {
        MetricId.RPM -> R.string.metric_rpm
        MetricId.SPEED -> R.string.metric_speed
        MetricId.COOLANT_TEMPERATURE -> R.string.metric_coolant_temperature
        MetricId.ENGINE_LOAD -> R.string.metric_engine_load
        MetricId.THROTTLE_POSITION -> R.string.metric_throttle_position
        MetricId.INTAKE_MANIFOLD_PRESSURE -> R.string.metric_intake_manifold_pressure
        MetricId.MAF -> R.string.metric_maf
        MetricId.FUEL_LEVEL -> R.string.metric_fuel_level
    }
