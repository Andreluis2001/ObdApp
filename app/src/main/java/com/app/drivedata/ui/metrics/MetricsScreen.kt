package com.app.drivedata.ui.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.formatClockTime
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.common.formatMetricValue
import com.app.drivedata.ui.common.isSameDay
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.theme.LoginBackgroundTop
import com.app.drivedata.ui.theme.LoginTextMuted

private enum class ParametersTab { REALTIME, DIAGNOSTICS }

@Composable
fun MetricsScreen(
    viewModel: MetricsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(ParametersTab.REALTIME) }
    LightStatusBarIcons()
    MetricsContent(
        state = state,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onToggleCollection = {
            if (state.isCollecting) viewModel.stopCollection() else viewModel.startCollection()
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun MetricsContent(
    state: MetricsUiState,
    selectedTab: ParametersTab,
    onTabSelected: (ParametersTab) -> Unit,
    onToggleCollection: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        ParametersHeader(
            vehicleName = state.vehicleName,
            isCollecting = state.isCollecting,
            canToggleCollection = state.isEcuVerified,
            onToggleCollection = onToggleCollection,
            onBack = onBack,
        )
        ParametersTabs(selected = selectedTab, onSelected = onTabSelected)
        when (selectedTab) {
            ParametersTab.REALTIME -> RealtimeParameters(state = state, modifier = Modifier.weight(1f))
            ParametersTab.DIAGNOSTICS -> Diagnostics(state = state, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ParametersHeader(
    vehicleName: String,
    isCollecting: Boolean,
    canToggleCollection: Boolean,
    onToggleCollection: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LoginBackgroundTop)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.metrics_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = vehicleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = LoginTextMuted,
                )
            }
            if (canToggleCollection) {
                IconButton(onClick = onToggleCollection) {
                    Icon(
                        imageVector =
                            if (isCollecting) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (isCollecting) {
                                R.string.metrics_pause_collection
                            } else {
                                R.string.metrics_resume_collection
                            },
                        ),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametersTabs(selected: ParametersTab, onSelected: (ParametersTab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HomeSurface)
                .height(48.dp),
        ) {
            ParametersTabItem(
                label = stringResource(R.string.metrics_tab_realtime),
                selected = selected == ParametersTab.REALTIME,
                onClick = { onSelected(ParametersTab.REALTIME) },
                modifier = Modifier.weight(1f),
            )
            ParametersTabItem(
                label = stringResource(R.string.metrics_tab_diagnostics),
                selected = selected == ParametersTab.DIAGNOSTICS,
                onClick = { onSelected(ParametersTab.DIAGNOSTICS) },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = HomeDivider)
    }
}

@Composable
private fun ParametersTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) HomeBlue else HomeTextSecondary,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.7f)
                .height(2.dp)
                .background(if (selected) HomeBlue else Color.Transparent, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun RealtimeParameters(state: MetricsUiState, modifier: Modifier = Modifier) {
    val readings = state.snapshot?.readings.orEmpty().associateBy(MetricReading::id)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.isEcuVerified) {
            Text(
                text = stringResource(R.string.metrics_offline_hint),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HomeBlue.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        parameterOrder.forEach { id ->
            MetricCard(
                presentation = id.presentation(),
                reading = readings[id],
                history = state.history[id].orEmpty(),
            )
        }

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = HomeRed,
            )
        }

        Text(
            text = stringResource(R.string.metrics_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = HomeTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun MetricCard(
    presentation: MetricPresentation,
    reading: MetricReading?,
    history: List<Double>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .border(
                        width = 2.dp,
                        color = presentation.tint.copy(alpha = 0.45f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = null,
                    tint = presentation.tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(presentation.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = HomeTextSecondary,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    val available = reading?.value as? ReadingValue.Available
                    Text(
                        text = available?.let { formatMetricValue(it.value) }
                            ?: stringResource(R.string.home_value_unavailable),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeTextPrimary,
                    )
                    if (available != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(presentation.unitRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = HomeTextSecondary,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.metrics_min_max,
                        formatMetricValue(presentation.range.start),
                        formatMetricValue(presentation.range.endInclusive),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTextSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Sparkline(
                points = history,
                color = presentation.tint,
                modifier = Modifier
                    .width(82.dp)
                    .height(38.dp),
            )
        }
    }
}

/** Draws a compact line chart of the latest samples, with a soft area fill under the line. */
@Composable
private fun Sparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        if (points.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val min = points.min()
        val max = points.max()
        val span = max - min
        val stepX = size.width / points.lastIndex
        val verticalPadding = 3.dp.toPx()
        val usableHeight = (size.height - verticalPadding * 2f).coerceAtLeast(1f)

        fun pointAt(index: Int): Offset {
            val ratio =
                if (span > 1e-9) ((points[index] - min) / span).toFloat() else 0.5f
            return Offset(
                x = index * stepX,
                y = size.height - verticalPadding - ratio * usableHeight,
            )
        }

        val linePath = Path().apply {
            val first = pointAt(0)
            moveTo(first.x, first.y)
            for (index in 1..points.lastIndex) {
                val point = pointAt(index)
                lineTo(point.x, point.y)
            }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)),
            ),
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = color,
            radius = 2.5.dp.toPx(),
            center = pointAt(points.lastIndex),
        )
    }
}

@Composable
private fun Diagnostics(state: MetricsUiState, modifier: Modifier = Modifier) {
    val readings = state.snapshot?.readings.orEmpty()
    val availableCount = readings.count { it.value is ReadingValue.Available }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DiagnosticsCard(title = stringResource(R.string.metrics_diag_status)) {
            DiagnosticRow(
                label = stringResource(R.string.metrics_diag_connection),
                value = state.connection.statusLabel(),
                valueColor = state.connection.statusColor(),
            )
            DiagnosticRow(
                label = stringResource(R.string.metrics_diag_protocol),
                value = state.connection.protocolName() ?: stringResource(R.string.metrics_unavailable),
            )
            DiagnosticRow(
                label = stringResource(R.string.metrics_diag_adapter),
                value = stringResource(R.string.metrics_diag_adapter_value),
            )
        }

        DiagnosticsCard(title = stringResource(R.string.metrics_diag_information)) {
            DiagnosticRow(
                label = stringResource(R.string.metrics_diag_vehicle),
                value = state.vehicleName,
            )
            DiagnosticRow(
                label = stringResource(R.string.metrics_diag_vin),
                value = state.snapshot?.vin ?: stringResource(R.string.metrics_unavailable),
            )
            DiagnosticRow(
                label = stringResource(R.string.metrics_diag_last_reading),
                value = lastReadingLabel(state.snapshot?.timestampMillis),
            )
            DiagnosticRow(
                label = stringResource(R.string.metrics_diag_parameters),
                value = stringResource(
                    R.string.metrics_diag_parameters_value,
                    availableCount,
                    parameterOrder.size,
                ),
            )
        }

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = HomeRed,
            )
        }

        Text(
            text = stringResource(R.string.metrics_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = HomeTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun DiagnosticsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
            )
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, valueColor: Color = HomeTextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun lastReadingLabel(millis: Long?): String {
    if (millis == null) return stringResource(R.string.metrics_diag_never)
    return if (isSameDay(millis)) {
        stringResource(R.string.home_sync_today, formatClockTime(millis))
    } else {
        formatDateTime(millis)
    }
}

@Composable
private fun ObdConnectionState.statusLabel(): String =
    when (this) {
        is ObdConnectionState.Disconnected -> stringResource(R.string.metrics_status_disconnected)
        is ObdConnectionState.Connecting -> stringResource(R.string.metrics_status_connecting)
        is ObdConnectionState.Initializing -> stringResource(R.string.metrics_status_initializing)
        is ObdConnectionState.Connected -> when (ecuStatus) {
            EcuStatus.VERIFIED -> stringResource(R.string.metrics_status_verified)
            EcuStatus.NO_COMMUNICATION -> stringResource(R.string.metrics_status_ecu_no_response)
            EcuStatus.VERIFYING, EcuStatus.UNKNOWN -> stringResource(R.string.metrics_status_verifying)
        }
        is ObdConnectionState.Failed -> stringResource(R.string.metrics_status_failed)
    }

@Composable
private fun ObdConnectionState.statusColor(): Color =
    when (this) {
        is ObdConnectionState.Connected ->
            when (ecuStatus) {
                EcuStatus.VERIFIED -> HomeGreen
                EcuStatus.NO_COMMUNICATION -> HomeRed
                EcuStatus.VERIFYING, EcuStatus.UNKNOWN -> HomeTextSecondary
            }
        is ObdConnectionState.Failed -> HomeRed
        else -> HomeTextSecondary
    }

private fun ObdConnectionState.protocolName(): String? =
    (this as? ObdConnectionState.Connected)?.protocol

private fun MetricsError.messageRes(): Int =
    when (this) {
        MetricsError.STORAGE -> R.string.metrics_error_storage
        MetricsError.UNEXPECTED -> R.string.metrics_error_unexpected
    }
