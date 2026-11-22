package com.app.drivedata.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.ui.common.AppCard
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.MetricSparkline
import com.app.drivedata.ui.common.SectionHeader
import com.app.drivedata.ui.common.TintedIconBadge
import com.app.drivedata.ui.common.formatBytes
import com.app.drivedata.ui.common.formatMetricValue
import com.app.drivedata.ui.common.lastSyncText
import com.app.drivedata.ui.common.syncErrorMessage
import com.app.drivedata.ui.metrics.MetricPresentation
import com.app.drivedata.ui.metrics.presentation
import com.app.drivedata.ui.preview.previewHomeIdleState
import com.app.drivedata.ui.preview.previewHomeState
import com.app.drivedata.ui.theme.Dimens
import com.app.drivedata.ui.theme.DriveDataTheme
import com.app.drivedata.ui.theme.HeaderGradient
import com.app.drivedata.ui.theme.HeaderTextMuted
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.theme.LoginField
import com.app.drivedata.ui.theme.LoginFieldBorder
import com.app.drivedata.vehicle.model.Vehicle

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSeeAllReadings: () -> Unit,
    onOpenVehicles: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LightStatusBarIcons()
    HomeContent(
        state = state,
        onSeeAllReadings = onSeeAllReadings,
        onOpenVehicles = onOpenVehicles,
        onSelectVehicle = onSelectVehicle,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSeeAllReadings: () -> Unit,
    onOpenVehicles: () -> Unit,
    onSelectVehicle: (String) -> Unit,
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
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            CollectionCard(state = state)

            SectionHeader(stringResource(R.string.home_main_parameters))
            MainParameters(history = state.history, snapshot = state.snapshot)
            SeeAllRow(onClick = onSeeAllReadings)

            SectionHeader(stringResource(R.string.home_connection_status))
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
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val orderedVehicles = vehicles.sortedByDescending { it.isActive }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderGradient)
            .statusBarsPadding()
            .padding(start = Dimens.ScreenPadding, end = 4.dp, top = 4.dp, bottom = 16.dp),
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
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Dimens.InnerCorner))
                    .background(LoginField)
                    .border(
                        width = 1.dp,
                        color = LoginFieldBorder,
                        shape = RoundedCornerShape(Dimens.InnerCorner),
                    )
                    .clickable {
                        if (vehicles.isEmpty()) {
                            onManageVehicles()
                        } else {
                            menuExpanded = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    color = if (hasVehicle) Color.White else HeaderTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = stringResource(R.string.home_select_vehicle),
                    tint = HeaderTextMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(Dimens.InnerCorner),
                containerColor = HomeSurface,
            ) {
                orderedVehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = vehicle.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
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
                                    contentDescription = stringResource(R.string.vehicle_active_badge),
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
                    text = {
                        Text(
                            stringResource(R.string.vehicles_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HomeTextPrimary,
                        )
                    },
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
    AppCard(
        elevation = 3.dp,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CollectionRing(isCollecting = state.isCollecting)
            Spacer(Modifier.width(14.dp))
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
                    status = if (adapterConnected) stringResource(R.string.home_connected_value) else null,
                )
                DetailRow(
                    icon = Icons.Filled.Sync,
                    label = stringResource(R.string.home_last_sync),
                    value = lastSyncText(state.lastSyncedAtMillis),
                )
                DetailRow(
                    icon = Icons.Filled.CloudUpload,
                    label = stringResource(R.string.home_pending_data),
                    value = stringResource(
                        R.string.home_pending_value,
                        state.pendingCount,
                        formatBytes(state.pendingBytes),
                    ),
                )
                if (state.rejectedCount > 0) {
                    DetailRow(
                        icon = Icons.Filled.ErrorOutline,
                        label = stringResource(R.string.home_rejected_data),
                        value = stringResource(R.string.home_rejected_value, state.rejectedCount),
                        valueColor = HomeRed,
                    )
                }
                state.syncError?.let { error ->
                    Text(
                        text = error.syncErrorMessage(),
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
            val strokeWidth = 7.dp.toPx()
            val radius = size.minDimension / 2f - strokeWidth / 2f
            drawCircle(color = accent.copy(alpha = 0.10f), radius = radius)
            drawCircle(
                color = accent,
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(
            modifier = Modifier.width(84.dp),
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
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = HomeTextPrimary,
    status: String? = null,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HomeTextSecondary,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 1.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = HomeTextSecondary,
                maxLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    maxLines = 2,
                )
                if (status != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HomeTextSecondary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = HomeTextPrimary,
                        maxLines = 2,
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
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.GridSpacing)) {
        mainMetrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.GridSpacing),
            ) {
                rowMetrics.forEach { id ->
                    MetricTile(
                        presentation = id.presentation(),
                        value = snapshot.reading(id),
                        points = history[id].orEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                if (rowMetrics.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
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
    val available = value as? ReadingValue.Available
    AppCard(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TintedIconBadge(
                icon = presentation.icon,
                tint = presentation.tint,
                badgeSize = 32.dp,
                iconSize = 18.dp,
            )
            Text(
                text = stringResource(presentation.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = HomeTextSecondary,
                maxLines = 2,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = available?.let { formatMetricValue(it.value) }
                    ?: stringResource(R.string.home_value_unavailable),
                style = MaterialTheme.typography.titleLarge,
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
        MetricSparkline(
            points = if (available != null) points else emptyList(),
            color = presentation.tint,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        )
        Text(
            text = stringResource(
                R.string.metrics_min_max,
                formatMetricValue(presentation.range.start),
                formatMetricValue(presentation.range.endInclusive),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = HomeTextSecondary,
            maxLines = 2,
        )
    }
}

@Composable
private fun SeeAllRow(onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(Dimens.CardPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_see_all_parameters),
                style = MaterialTheme.typography.labelLarge,
                color = HomeBlue,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = HomeBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ConnectionStatusCards(state: HomeUiState) {
    val adapterConnected = state.connection is ObdConnectionState.Connected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Dimens.GridSpacing),
    ) {
        StatusCard(
            icon = Icons.Filled.SettingsInputAntenna,
            iconColor = if (adapterConnected) HomeGreen else HomeTextSecondary,
            label = stringResource(R.string.home_adapter),
            value = stringResource(
                if (adapterConnected) R.string.home_connected_value else R.string.home_disconnected_value,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        StatusCard(
            icon = Icons.Filled.Wifi,
            iconColor = if (state.internetAvailable) HomeGreen else HomeRed,
            label = stringResource(R.string.home_internet),
            value = stringResource(
                if (state.internetAvailable) R.string.home_available else R.string.home_unavailable,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        StatusCard(
            icon = Icons.Filled.Storage,
            iconColor = HomeTextSecondary,
            label = stringResource(R.string.home_storage),
            value = stringResource(
                R.string.home_storage_value,
                formatBytes(state.storageUsedBytes),
                formatBytes(state.storageBudgetBytes),
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = HomeTextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun MetricsSnapshot?.reading(id: MetricId): ReadingValue? =
    this?.readings?.firstOrNull { it.id == id }?.value

@Preview(
    showBackground = true,
    backgroundColor = 0xFFEEF3FA,
    widthDp = 360,
    heightDp = 800,
    name = "Home - collecting",
)
@Composable
private fun HomeCollectingPreview() {
    DriveDataTheme {
        HomeContent(
            state = previewHomeState(),
            onSeeAllReadings = {},
            onOpenVehicles = {},
            onSelectVehicle = {},
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFEEF3FA,
    widthDp = 320,
    heightDp = 760,
    fontScale = 1.3f,
    name = "Home - idle, small width, large text",
)
@Composable
private fun HomeIdlePreview() {
    DriveDataTheme {
        HomeContent(
            state = previewHomeIdleState(),
            onSeeAllReadings = {},
            onOpenVehicles = {},
            onSelectVehicle = {},
        )
    }
}
