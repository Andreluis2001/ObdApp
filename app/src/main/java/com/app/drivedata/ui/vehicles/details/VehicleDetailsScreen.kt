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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.metrics.MetricDefinition
import com.app.drivedata.metrics.defaultMetricCatalog
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.metrics.presentation
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.vehicles.components.ActiveBadge
import com.app.drivedata.ui.vehicles.components.VehicleErrorBanner
import com.app.drivedata.ui.vehicles.components.VehicleHeader
import com.app.drivedata.ui.vehicles.components.color
import com.app.drivedata.ui.vehicles.components.label
import com.app.drivedata.ui.vehicles.components.lastCommunicationText
import com.app.drivedata.ui.vehicles.messageRes
import com.app.drivedata.vehicle.model.SupportedPid
import com.app.drivedata.vehicle.model.Vehicle

@Composable
fun VehicleDetailsScreen(
    viewModel: VehicleDetailsViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
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
                        onEdit = { onEdit(vehicle.id) },
                    )
                    AdapterCard(vehicle = vehicle)
                    CapabilitiesCard(
                        vehicle = vehicle,
                        catalog = catalog,
                        enabled = !state.isBusy,
                        onPidToggle = viewModel::setPidEnabled,
                        onRediscover = viewModel::rediscover,
                    )
                    ActionsCard(
                        vehicle = vehicle,
                        enabled = !state.isBusy,
                        onSelectActive = viewModel::setActive,
                        onEdit = { onEdit(vehicle.id) },
                        onRemove = { confirmRemoval = true },
                    )
                    Spacer(Modifier.height(8.dp))
                }
        }
    }

    if (confirmRemoval && vehicle != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
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
private fun VehicleInfoCard(vehicle: Vehicle, onEdit: () -> Unit) {
    SectionCard(title = stringResource(R.string.vehicle_details_info)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                Text(
                    text = "${vehicle.details.make} ${vehicle.details.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
            }
            if (vehicle.isActive) {
                ActiveBadge()
            }
        }
        HorizontalDivider(color = HomeDivider)
        vehicle.details.nickname?.let {
            InfoRow(stringResource(R.string.vehicle_details_nickname), it)
        }
        vehicle.details.year?.let {
            InfoRow(stringResource(R.string.vehicle_details_year), it.toString())
        }
        vehicle.details.licensePlate?.let {
            InfoRow(stringResource(R.string.vehicle_details_plate), it)
        }
        TextButton(onClick = onEdit) {
            Text(stringResource(R.string.vehicle_details_edit))
        }
    }
}

@Composable
private fun AdapterCard(vehicle: Vehicle) {
    val connection = vehicle.connection
    SectionCard(title = stringResource(R.string.vehicle_details_adapter)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(9.dp)
                        .background(connection.status.color(), CircleShape),
            )
            Text(
                text = connection.status.label(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = connection.status.color(),
            )
        }
        InfoRow(
            label = stringResource(R.string.connection_address_label),
            value =
                if (connection.isConfigured) {
                    stringResource(R.string.connection_address, connection.host, connection.port)
                } else {
                    stringResource(R.string.connection_address_unknown)
                },
        )
        InfoRow(
            label = stringResource(R.string.vehicle_details_last_connection),
            value = lastCommunicationText(connection.lastConnectionAtMillis),
        )
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

    SectionCard(title = stringResource(R.string.vehicle_details_capabilities)) {
        if (discoveredAt == null) {
            Text(
                text = stringResource(R.string.vehicle_details_no_capabilities),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
            )
        } else {
            Text(
                text =
                    stringResource(
                        R.string.add_vehicle_discovered_at,
                        formatDateTime(discoveredAt),
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
            )
        }

        if (discovered.isEmpty()) {
            if (discoveredAt != null) {
                Text(
                    text = stringResource(R.string.vehicle_details_no_capabilities),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
            }
        } else {
            discovered.forEach { (definition, supported) ->
                DiscoveredPidRow(
                    definition = definition,
                    supported = supported,
                    enabled = enabled,
                    onToggle = onPidToggle,
                )
            }
        }

        OutlinedButton(
            onClick = onRediscover,
            enabled = enabled && vehicle.connection.isConfigured,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_vehicle_rediscover))
        }
    }
}

@Composable
private fun DiscoveredPidRow(
    definition: MetricDefinition,
    supported: SupportedPid,
    enabled: Boolean,
    onToggle: (Int, Boolean) -> Unit,
) {
    val presentation = definition.id.presentation()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .background(presentation.tint.copy(alpha = 0.14f), CircleShape),
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = HomeTextPrimary,
            )
            Text(
                text =
                    stringResource(presentation.unitRes) +
                        " · " +
                        stringResource(R.string.add_vehicle_pid_value, supported.pid),
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
            )
        }
        Switch(
            checked = supported.enabledForCollection,
            onCheckedChange = { checked -> onToggle(supported.pid, checked) },
            enabled = enabled,
        )
    }
}

@Composable
private fun ActionsCard(
    vehicle: Vehicle,
    enabled: Boolean,
    onSelectActive: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.vehicle_details_actions)) {
        if (!vehicle.isActive) {
            OutlinedButton(
                onClick = onSelectActive,
                enabled = enabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.vehicle_select_active))
            }
        }
        OutlinedButton(
            onClick = onEdit,
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.vehicle_details_edit))
        }
        TextButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = HomeRed),
        ) {
            Text(stringResource(R.string.vehicle_remove))
        }
    }
}

@Composable
private fun BusyCard(operation: DetailsOperation) {
    SectionCard(title = stringResource(R.string.vehicle_details_rediscovering)) {
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
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HomeTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTextPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
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
