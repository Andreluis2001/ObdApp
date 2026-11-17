package com.app.drivedata.ui.vehicles.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.metrics.MetricDefinition
import com.app.drivedata.metrics.defaultMetricCatalog
import com.app.drivedata.ui.common.InfoRow
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.PrimaryButton
import com.app.drivedata.ui.common.RowDivider
import com.app.drivedata.ui.common.SectionHeader
import com.app.drivedata.ui.common.SecondaryButton
import com.app.drivedata.ui.common.StatusLabel
import com.app.drivedata.ui.common.TintedIconBadge
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.discovery.messageRes
import com.app.drivedata.ui.metrics.presentation
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.vehicles.components.ActiveBadge
import com.app.drivedata.ui.vehicles.components.DiscoveredPidRow
import com.app.drivedata.ui.vehicles.components.VehicleErrorBanner
import com.app.drivedata.ui.vehicles.components.VehicleHeader
import com.app.drivedata.ui.vehicles.components.color
import com.app.drivedata.ui.vehicles.components.label
import com.app.drivedata.ui.vehicles.components.lastCommunicationText
import com.app.drivedata.vehicle.model.SupportedPid
import com.app.drivedata.vehicle.model.Vehicle

@Composable
fun VehicleDetailsScreen(
    viewModel: VehicleDetailsViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    catalog: List<MetricDefinition> = defaultMetricCatalog,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    val vehicle = state.vehicle

    LaunchedEffect(state.removed) {
        if (state.removed) onDeleted()
    }

    LightStatusBarIcons()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeBackground),
    ) {
        VehicleHeader(
            title = vehicle?.displayName ?: stringResource(R.string.vehicle_details_title),
            subtitle = stringResource(R.string.vehicle_details_subtitle),
            onBack = onBack,
        )

        when {
            state.isLoading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = HomeBlue)
                }

            vehicle == null ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.vehicle_details_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HomeTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

            else ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.error?.let { error ->
                        VehicleErrorBanner(
                            message = error.message(),
                            onDismiss = viewModel::dismissError,
                        )
                    }

                    if (state.isBusy) {
                        BusyCard(operation = state.operation)
                    }

                    VehicleInfoCard(
                        vehicle = vehicle,
                        enabled = !state.isBusy,
                        onSelectActive = viewModel::setActive,
                        onRemove = { confirmRemoval = true },
                    )

                    SectionHeader(title = stringResource(R.string.vehicle_details_adapter))
                    AdapterCard(vehicle = vehicle)

                    SectionHeader(title = stringResource(R.string.vehicle_details_capabilities))
                    CapabilitiesCard(
                        vehicle = vehicle,
                        catalog = catalog,
                        enabled = !state.isBusy,
                        onPidToggle = viewModel::setPidEnabled,
                        onRediscover = viewModel::rediscover,
                    )
                    Spacer(Modifier.height(8.dp))
                }
        }
    }

    if (confirmRemoval && vehicle != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            containerColor = HomeSurface,
            titleContentColor = HomeTextPrimary,
            textContentColor = HomeTextSecondary,
            title = { Text(stringResource(R.string.vehicle_remove_title)) },
            text = {
                Text(stringResource(R.string.vehicle_remove_message, vehicle.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoval = false
                        viewModel.delete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.vehicle_remove_confirm),
                        color = HomeRed,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun VehicleInfoCard(
    vehicle: Vehicle,
    enabled: Boolean,
    onSelectActive: () -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(HomeBlue.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = HomeBlue,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = vehicle.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        vehicle.identity?.vin
                            ?: "${vehicle.details.make} ${vehicle.details.model}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (vehicle.isActive) {
                    Spacer(Modifier.height(2.dp))
                    ActiveBadge()
                }
            }
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.vehicle_remove),
                    tint = HomeRed,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (!vehicle.isActive) {
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                label = stringResource(R.string.vehicle_select_active),
                onClick = onSelectActive,
                enabled = enabled,
            )
        }

        vehicle.details.licensePlate?.let { plate ->
            RowDivider()
            InfoRow(label = stringResource(R.string.vehicle_details_plate), value = plate)
        }

        vehicle.identity?.let { identity ->
            RowDivider()
            Text(
                text = stringResource(R.string.vehicle_details_identity),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
            )
            InfoRow(label = stringResource(R.string.vehicle_vin_label), value = identity.vin)
            InfoRow(
                label = stringResource(R.string.vehicle_manufacturer_label),
                value = identity.manufacturer ?: stringResource(R.string.vehicle_identity_unavailable),
            )
            InfoRow(
                label = stringResource(R.string.vehicle_serial_label),
                value = identity.serialNumber ?: stringResource(R.string.vehicle_identity_unavailable),
            )
        }
    }
}

@Composable
private fun AdapterCard(vehicle: Vehicle) {
    val connection = vehicle.connection
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TintedIconBadge(
                icon = Icons.Filled.Wifi,
                tint = connection.status.color(),
            )
            Spacer(Modifier.width(12.dp))
            StatusLabel(
                color = connection.status.color(),
                label = connection.status.label(),
            )
        }
        RowDivider()
        InfoRow(
            label = stringResource(R.string.connection_address_label),
            value =
                if (connection.isConfigured) {
                    stringResource(R.string.connection_address, connection.host, connection.port)
                } else {
                    stringResource(R.string.connection_address_unknown)
                },
        )
        Spacer(Modifier.height(10.dp))
        InfoRow(
            label = stringResource(R.string.vehicle_details_last_connection),
            value = lastCommunicationText(connection.lastConnectionAtMillis),
        )
        Spacer(Modifier.height(10.dp))
        InfoRow(
            label = stringResource(R.string.vehicle_last_ecu),
            value = lastCommunicationText(connection.lastEcuCommunicationAtMillis),
        )
    }
}

@Composable
private fun CapabilitiesCard(
    vehicle: Vehicle,
    catalog: List<MetricDefinition>,
    enabled: Boolean,
    onPidToggle: (Int, Boolean) -> Unit,
    onRediscover: () -> Unit,
) {
    val supportedByPid = vehicle.capabilities.supportedPids.associateBy { it.pid }
    val discovered =
        catalog.mapNotNull { definition ->
            supportedByPid[definition.pid]?.let { supported -> definition to supported }
        }
    val discoveredAt = vehicle.capabilities.lastDiscoveredAtMillis

    SectionCard {
        if (discovered.isEmpty()) {
            Text(
                text = stringResource(R.string.vehicle_details_no_capabilities),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
            )
            if (discoveredAt != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text =
                        stringResource(
                            R.string.vehicle_discovered_at,
                            formatDateTime(discoveredAt),
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTextSecondary,
                )
            }
        } else {
            Spacer(Modifier.height(4.dp))
            discovered.forEachIndexed { index, (definition, supported) ->
                if (index > 0) RowDivider(verticalPadding = 6.dp)
                DiscoveredPidRow(
                    metricId = definition.id,
                    pid = supported.pid,
                    subtitle =
                        stringResource(definition.id.presentation().unitRes) + " · " +
                            stringResource(R.string.pid_value, supported.pid),
                    checked = supported.enabledForCollection,
                    enabled = enabled,
                    onToggle = onPidToggle,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        SecondaryButton(
            label = stringResource(R.string.vehicle_rediscover),
            onClick = onRediscover,
            enabled = enabled && vehicle.connection.isConfigured,
        )
    }
}

@Composable
private fun BusyCard(operation: DetailsOperation) {
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = HomeBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text =
                        stringResource(
                            when (operation) {
                                DetailsOperation.Idle -> R.string.vehicle_details_rediscovering
                                DetailsOperation.Connecting -> R.string.vehicle_details_connecting
                                is DetailsOperation.Discovering -> R.string.vehicle_details_discovering
                            },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeTextPrimary,
                )
                if (operation is DetailsOperation.Discovering) {
                    Text(
                        text =
                            stringResource(
                                R.string.vehicle_details_discovery_progress,
                                operation.rangeBase,
                                operation.foundCount,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = HomeTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DetailsError.message(): String =
    when (this) {
        DetailsError.NotConfigured -> stringResource(R.string.vehicle_details_not_configured)
        is DetailsError.Rediscovery ->
            stringResource(
                R.string.vehicle_details_rediscovery_failed,
                stringResource(failure.messageRes()),
            )

        DetailsError.PidUpdateFailed -> stringResource(R.string.vehicle_details_pid_update_failed)
        DetailsError.DeleteFailed -> stringResource(R.string.vehicle_details_delete_failed)
        DetailsError.ActivateFailed -> stringResource(R.string.vehicle_details_activate_failed)
    }
