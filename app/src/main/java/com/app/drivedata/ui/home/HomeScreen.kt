package com.app.drivedata.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.sync.SyncError
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.formatBytes
import com.app.drivedata.ui.common.formatClockTime
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.common.formatMetricValue
import com.app.drivedata.ui.common.isSameDay
import com.app.drivedata.ui.common.MetricSparkline
import com.app.drivedata.ui.common.TintedIconBadge
import com.app.drivedata.ui.metrics.MetricPresentation
import com.app.drivedata.ui.metrics.presentation
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeOrange
import com.app.drivedata.ui.theme.HomePurple
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.theme.LoginBackgroundTop
import com.app.drivedata.ui.theme.LoginField
import com.app.drivedata.ui.theme.LoginFieldBorder
import com.app.drivedata.ui.theme.LoginTextMuted
import com.app.drivedata.vehicle.model.Vehicle

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSeeAllReadings: () -> Unit,
    onOpenVehicles: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LightStatusBarIcons()
    HomeContent(
        state = state,
        onSeeAllReadings = onSeeAllReadings,
        onOpenVehicles = onOpenVehicles,
        onSelectVehicle = onSelectVehicle,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSeeAllReadings: () -> Unit,
    onOpenVehicles: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        HomeHeader(
            isConnected = state.isConnected,
            vehicleName = state.vehicleName,
            hasVehicle = state.hasVehicle,
            vehicles = state.vehicles,
            onSelectVehicle = onSelectVehicle,
            onManageVehicles = onOpenVehicles,
            onSignOut = onSignOut,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CollectionCard(state = state)

            SectionTitle(stringResource(R.string.home_main_parameters))
            MainParameters(history = state.history, snapshot = state.snapshot)
            SeeAllRow(onClick = onSeeAllReadings)

            SectionTitle(stringResource(R.string.home_connection_status))
            ConnectionStatusCards(state = state)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeHeader(
    isConnected: Boolean,
    vehicleName: String,
    hasVehicle: Boolean,
    vehicles: List<Vehicle>,
    onSelectVehicle: (String) -> Unit,
    onManageVehicles: () -> Unit,
    onSignOut: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val orderedVehicles = vehicles.sortedByDescending { it.isActive }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LoginBackgroundTop)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(if (isConnected) HomeGreen else HomeTextSecondary, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(
                    if (isConnected) R.string.home_connected else R.string.home_disconnected,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = HomeRed),
            ) {
                Text(
                    text = stringResource(R.string.action_sign_out),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LoginField)
                    .border(
                        width = 1.dp,
                        color = LoginFieldBorder,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable {
                        if (vehicles.isEmpty()) {
                            onManageVehicles()
                        } else {
                            menuExpanded = true
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(HomeBlue.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = HomeBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text =
                        if (hasVehicle) {
                            vehicleName
                        } else {
                            stringResource(R.string.home_no_active_vehicle)
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasVehicle) Color.White else LoginTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.home_select_vehicle),
                    tint = LoginTextMuted,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(16.dp),
                containerColor = HomeSurface,
            ) {
                orderedVehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = vehicle.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (vehicle.isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = HomeTextPrimary,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSelectVehicle(vehicle.id)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.DirectionsCar,
                                contentDescription = null,
                                tint = if (vehicle.isActive) HomeBlue else HomeTextSecondary,
                            )
                        },
                        trailingIcon = if (vehicle.isActive) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = HomeBlue,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
                if (vehicles.isNotEmpty()) {
                    HorizontalDivider(color = HomeDivider)
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vehicles_title), color = HomeTextPrimary) },
                    onClick = {
                        menuExpanded = false
                        onManageVehicles()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = HomeTextSecondary,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CollectionCard(state: HomeUiState) {
    val adapterConnected = state.connection is ObdConnectionState.Connected
    Card(
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CollectionRing(isCollecting = state.isCollecting)
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailRow(
                    icon = Icons.Filled.Wifi,
                    label = stringResource(R.string.home_obd_connection),
                    value = if (adapterConnected) {
                        stringResource(R.string.home_transport_wifi)
                    } else {
                        stringResource(R.string.home_disconnected_value)
                    },
                    valueColor = HomeTextPrimary,
                    status = if (adapterConnected) stringResource(R.string.home_connected_value) else null,
                )
                DetailRow(
                    icon = Icons.Filled.Sync,
                    label = stringResource(R.string.home_last_sync),
                    value = lastSyncText(state.lastSyncedAtMillis),
                    valueColor = HomeTextPrimary,
                )
                DetailRow(
                    icon = Icons.Filled.Inbox,
                    label = stringResource(R.string.home_pending_data),
                    value = stringResource(
                        R.string.home_pending_value,
                        state.pendingCount,
                        formatBytes(state.pendingBytes),
                    ),
                    valueColor = HomeTextPrimary,
                )
                if (state.rejectedCount > 0) {
                    DetailRow(
                        icon = Icons.Filled.Warning,
                        label = stringResource(R.string.home_rejected_data),
                        value = stringResource(R.string.home_rejected_value, state.rejectedCount),
                        valueColor = HomeRed,
                    )
                }
                state.syncError?.let { error ->
                    Text(
                        text = stringResource(error.messageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionRing(isCollecting: Boolean) {
    val accent = if (isCollecting) HomeGreen else HomeRed
    Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = accent,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(
            modifier = Modifier.width(86.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(if (isCollecting) R.string.home_collecting else R.string.home_idle),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    status: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HomeTextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    maxLines = 1,
                )
                if (status != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTextSecondary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = HomeGreen,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainParameters(history: Map<MetricId, List<Double>>, snapshot: MetricsSnapshot?) {
    val mainMetrics =
        listOf(
            MetricId.RPM,
            MetricId.SPEED,
            MetricId.COOLANT_TEMPERATURE,
            MetricId.ENGINE_LOAD,
        )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        mainMetrics.chunked(2).forEach { rowMetrics ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowMetrics.forEach { id ->
                    MetricTile(
                        presentation = id.presentation(),
                        value = snapshot.reading(id),
                        points = history[id].orEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (rowMetrics.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(
    presentation: MetricPresentation,
    value: ReadingValue?,
    points: List<Double>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TintedIconBadge(
                    icon = presentation.icon,
                    tint = presentation.tint,
                    badgeSize = 30.dp,
                    iconSize = 17.dp,
                )
                Text(
                    text = stringResource(presentation.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTextSecondary,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = when (value) {
                        is ReadingValue.Available -> formatMetricValue(value.value)
                        else -> stringResource(R.string.home_value_unavailable)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(presentation.unitRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = HomeTextSecondary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricSparkline(
                    points = points,
                    color = presentation.tint,
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                )
                Text(
                    text = stringResource(
                        R.string.metrics_min_max,
                        formatMetricValue(presentation.range.start),
                        formatMetricValue(presentation.range.endInclusive),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SeeAllRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_see_all_parameters),
            style = MaterialTheme.typography.labelLarge,
            color = HomeBlue,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = HomeBlue,
        )
    }
}

@Composable
private fun ConnectionStatusCards(state: HomeUiState) {
    val adapterConnected = state.connection is ObdConnectionState.Connected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusCard(
            icon = Icons.Filled.Wifi,
            label = stringResource(R.string.home_adapter),
            value = stringResource(
                if (adapterConnected) R.string.home_connected_value else R.string.home_disconnected_value,
            ),
            valueColor = if (adapterConnected) HomeGreen else HomeTextSecondary,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        StatusCard(
            icon = Icons.Filled.Language,
            label = stringResource(R.string.home_internet),
            value = stringResource(
                if (state.internetAvailable) R.string.home_available else R.string.home_unavailable,
            ),
            valueColor = if (state.internetAvailable) HomeGreen else HomeRed,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        StatusCard(
            icon = Icons.Filled.Storage,
            label = stringResource(R.string.home_storage),
            value = stringResource(
                R.string.home_storage_value,
                formatBytes(state.storageUsedBytes),
                formatBytes(state.storageBudgetBytes),
            ),
            valueColor = HomeTextPrimary,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HomeTextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = HomeTextPrimary,
    )
}

@Composable
private fun lastSyncText(millis: Long?): String {
    if (millis == null) return stringResource(R.string.home_sync_never)
    val time = formatClockTime(millis)
    return if (isSameDay(millis)) {
        stringResource(R.string.home_sync_today, time)
    } else {
        formatDateTime(millis)
    }
}

@Composable
private fun SyncError.messageRes(): Int =
    when (this) {
        SyncError.NETWORK -> R.string.home_sync_error_network
        SyncError.AUTH -> R.string.home_sync_error_auth
        SyncError.SERVER -> R.string.home_sync_error_server
        SyncError.UNKNOWN -> R.string.home_sync_error_unknown
    }

private fun MetricsSnapshot?.reading(id: MetricId): ReadingValue? =
    this?.readings?.firstOrNull { it.id == id }?.value
