package com.app.drivedata.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.ui.common.InfoRow
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.PrimaryButton
import com.app.drivedata.ui.common.SecondaryButton
import com.app.drivedata.ui.common.StatusLabel
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.discovery.SetupStage
import com.app.drivedata.ui.discovery.labelRes
import com.app.drivedata.ui.discovery.messageRes
import com.app.drivedata.ui.metrics.presentation
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeOrange
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.theme.LoginBackgroundTop
import com.app.drivedata.ui.theme.LoginTextMuted
import com.app.drivedata.ui.vehicles.components.FormTextField

private enum class ConnectionDialog { PIDS, PROTOCOL }

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onBack: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LightStatusBarIcons()
    ConnectionContent(
        state = state,
        onBack = onBack,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onDiscover = viewModel::discoverVehicle,
        onCancel = viewModel::cancelDiscovery,
        onDisconnect = viewModel::disconnect,
        onOpenVehicle = onOpenVehicle,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionContent(
    state: ConnectionUiState,
    onBack: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDiscover: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeDialog by rememberSaveable { mutableStateOf<ConnectionDialog?>(null) }
    val focusManager = LocalFocusManager.current
    val protocol = (state.connection as? ObdConnectionState.Connected)?.protocol
    val protocolText =
        when {
            state.connection is ObdConnectionState.Connected ->
                protocol ?: stringResource(R.string.connection_protocol_unknown)

            else -> stringResource(R.string.connection_protocol_auto)
        }
    val address = adapterAddress(state)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        ConnectionHeader(onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .imeNestedScroll()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AdapterCard(
                state = state,
                onHostChange = onHostChange,
                onPortChange = onPortChange,
                onDiscover = onDiscover,
                onCancel = onCancel,
                onDisconnect = onDisconnect,
                hideKeyboard = focusManager::clearFocus,
            )

            when (val discovery = state.discovery) {
                is VehicleDiscovery.Running ->
                    DiscoveryProgressCard(stage = discovery.stage, discovery = discovery)

                is VehicleDiscovery.Failed ->
                    DiscoveryFailureCard(failure = discovery, onRetry = onDiscover)

                is VehicleDiscovery.Succeeded ->
                    DiscoverySuccessCard(
                        discovery = discovery,
                        onOpenVehicle = { onOpenVehicle(discovery.vehicleId) },
                    )

                VehicleDiscovery.Idle -> Unit
            }

            Text(
                text = stringResource(R.string.connection_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(
                    icon = Icons.Outlined.Settings,
                    title = stringResource(R.string.connection_pids),
                    subtitle = stringResource(R.string.connection_pids_subtitle),
                    onClick = { activeDialog = ConnectionDialog.PIDS },
                )
                SettingRow(
                    icon = Icons.Outlined.Build,
                    title = stringResource(R.string.connection_protocol),
                    subtitle = protocolText,
                    onClick = { activeDialog = ConnectionDialog.PROTOCOL },
                )
            }

            InfoBanner()

            Spacer(Modifier.height(8.dp))
        }
    }

    when (activeDialog) {
        ConnectionDialog.PIDS ->
            SupportedPidsDialog(
                summary = state.supportedPids,
                onDismiss = { activeDialog = null },
            )

        ConnectionDialog.PROTOCOL ->
            ProtocolDialog(
                protocol = protocolText,
                address = address,
                onDismiss = { activeDialog = null },
            )

        null -> Unit
    }
}

@Composable
private fun ConnectionHeader(onBack: () -> Unit) {
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
                    text = stringResource(R.string.connection_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.connection_header_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = LoginTextMuted,
                )
            }
        }
    }
}

@Composable
private fun AdapterCard(
    state: ConnectionUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDiscover: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    hideKeyboard: () -> Unit,
) {
    val connection = state.connection
    val discovering = state.isDiscovering

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(HomeBlue.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ImageAdapterIcon()
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    StatusLabel(
                        color = connection.statusColor(),
                        label = connection.statusLabel(),
                    )
                    Text(
                        text = stringResource(R.string.connection_adapter_name),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = HomeTextPrimary,
                    )
                    Text(
                        text = adapterAddress(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTextSecondary,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    value = state.host,
                    onValueChange = onHostChange,
                    label = stringResource(R.string.connection_host_label),
                    icon = Icons.Filled.Router,
                    isError = false,
                    errorText = "",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    enabled = !state.isBusy,
                )
                FormTextField(
                    value = state.port,
                    onValueChange = onPortChange,
                    label = stringResource(R.string.connection_port_label),
                    icon = Icons.Filled.SettingsEthernet,
                    isError = false,
                    errorText = "",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                hideKeyboard()
                                if (state.isConfigValid && !state.isBusy) onDiscover()
                            },
                        ),
                    enabled = !state.isBusy,
                )
            }

            Text(
                text = stringResource(R.string.connection_discover_hint),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
            )

            if (discovering) {
                PrimaryButton(
                    label = stringResource(R.string.connection_action_cancel),
                    onClick = onCancel,
                )
            } else {
                PrimaryButton(
                    label = stringResource(R.string.connection_discover),
                    onClick = onDiscover,
                    enabled = state.isConfigValid && !state.isBusy,
                )
                if (connection is ObdConnectionState.Connected ||
                    connection is ObdConnectionState.Failed
                ) {
                    SecondaryButton(
                        label = stringResource(R.string.connection_disconnect),
                        onClick = onDisconnect,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageAdapterIcon() {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.ic_elm327_adapter),
        contentDescription = null,
        modifier = Modifier.size(54.dp),
    )
}

@Composable
private fun DiscoveryProgressCard(
    stage: SetupStage,
    discovery: VehicleDiscovery.Running,
) {
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
                    text = stringResource(stage.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeTextPrimary,
                )
                if (stage == SetupStage.DISCOVERY) {
                    Text(
                        text =
                            stringResource(
                                R.string.discovery_progress,
                                discovery.rangeBase,
                                discovery.foundCount,
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
private fun DiscoveryFailureCard(
    failure: VehicleDiscovery.Failed,
    onRetry: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = HomeRed,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.connection_discovery_failed),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
            )
        }
        Text(
            text = stringResource(failure.stage.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = HomeTextSecondary,
        )
        Text(
            text = stringResource(failure.failure.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = HomeTextPrimary,
        )
        PrimaryButton(
            label = stringResource(R.string.connection_action_retry),
            onClick = onRetry,
        )
    }
}

@Composable
private fun DiscoverySuccessCard(
    discovery: VehicleDiscovery.Succeeded,
    onOpenVehicle: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(HomeGreen.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = HomeGreen,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text =
                        stringResource(
                            if (discovery.alreadyRegistered) {
                                R.string.connection_vehicle_already_registered
                            } else {
                                R.string.connection_vehicle_registered
                            },
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                Text(
                    text =
                        stringResource(
                            if (discovery.alreadyRegistered) {
                                R.string.connection_vehicle_already_registered_hint
                            } else {
                                R.string.connection_vehicle_registered_hint
                            },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
            }
        }

        Text(
            text = discovery.vehicleName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = HomeTextPrimary,
        )
        InfoRow(label = stringResource(R.string.vehicle_vin_label), value = discovery.vin)
        discovery.manufacturer?.let { manufacturer ->
            InfoRow(label = stringResource(R.string.vehicle_manufacturer_label), value = manufacturer)
        }
        discovery.serialNumber?.let { serialNumber ->
            InfoRow(label = stringResource(R.string.vehicle_serial_label), value = serialNumber)
        }

        PrimaryButton(
            label = stringResource(R.string.connection_vehicle_details),
            onClick = onOpenVehicle,
        )
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.5f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HomeTextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = HomeTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InfoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HomeBlue.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = HomeBlue,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.connection_info),
            style = MaterialTheme.typography.bodySmall,
            color = HomeTextPrimary,
        )
    }
}

@Composable
private fun SupportedPidsDialog(
    summary: SupportedPidsSummary,
    onDismiss: () -> Unit,
) {
    val vehicleName = summary.vehicleName
    val discoveredAt = summary.discoveredAtMillis

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomeSurface,
        titleContentColor = HomeTextPrimary,
        textContentColor = HomeTextSecondary,
        title = { Text(stringResource(R.string.connection_pids_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.connection_pids_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
                when {
                    vehicleName == null ->
                        EmptyDialogMessage(R.string.connection_pids_dialog_no_vehicle)

                    discoveredAt == null ->
                        EmptyDialogMessage(
                            R.string.connection_pids_dialog_no_discovery,
                            vehicleName,
                        )

                    summary.pids.isEmpty() ->
                        EmptyDialogMessage(R.string.connection_pids_dialog_no_catalog_pids)

                    else -> {
                        Text(
                            text =
                                stringResource(
                                    R.string.connection_pids_dialog_active_vehicle,
                                    vehicleName,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = HomeTextPrimary,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.connection_pids_dialog_discovered_at,
                                    formatDateTime(discoveredAt),
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = HomeTextSecondary,
                        )
                        summary.pids.forEach { item ->
                            SupportedPidRow(item)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun EmptyDialogMessage(
    textRes: Int,
    vararg formatArgs: Any,
) {
    Text(
        text = stringResource(textRes, *formatArgs),
        style = MaterialTheme.typography.bodySmall,
        color = HomeTextSecondary,
    )
}

@Composable
private fun SupportedPidRow(item: SupportedPidItem) {
    val presentation = item.definition.id.presentation()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = null,
            tint = presentation.tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(presentation.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextPrimary,
            )
            Text(
                text =
                    stringResource(presentation.unitRes) +
                        " · " +
                        stringResource(R.string.pid_value, item.definition.pid),
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
            )
        }
        if (!item.enabledForCollection) {
            Text(
                text = stringResource(R.string.connection_pids_dialog_collection_disabled),
                style = MaterialTheme.typography.labelSmall,
                color = HomeOrange,
            )
        }
    }
}

@Composable
private fun ProtocolDialog(
    protocol: String,
    address: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomeSurface,
        titleContentColor = HomeTextPrimary,
        textContentColor = HomeTextSecondary,
        title = { Text(stringResource(R.string.connection_protocol_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoLine(
                    label = stringResource(R.string.connection_protocol_dialog_current),
                    value = protocol,
                )
                InfoLine(
                    label = stringResource(R.string.connection_address_label),
                    value = address,
                )
                Text(
                    text = stringResource(R.string.connection_protocol_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
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
            color = HomeTextPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun adapterAddress(state: ConnectionUiState): String {
    val config = state.connection.configOrNull()
    val host = config?.host ?: state.host
    val port = config?.port?.toString() ?: state.port
    return if (host.isBlank() || port.isBlank()) {
        stringResource(R.string.connection_address_unknown)
    } else {
        stringResource(R.string.connection_address, host, port)
    }
}

@Composable
private fun ObdConnectionState.statusLabel(): String =
    when (this) {
        is ObdConnectionState.Disconnected -> stringResource(R.string.connection_status_disconnected)
        is ObdConnectionState.Connecting -> stringResource(R.string.connection_status_connecting)
        is ObdConnectionState.Initializing -> stringResource(R.string.connection_status_initializing)
        is ObdConnectionState.Connected -> when (ecuStatus) {
            EcuStatus.VERIFIED -> stringResource(R.string.connection_status_connected)
            EcuStatus.NO_COMMUNICATION -> stringResource(R.string.connection_status_no_ecu)
            EcuStatus.INVALID_RESPONSE -> stringResource(R.string.connection_status_invalid_response)
            EcuStatus.VERIFYING, EcuStatus.UNKNOWN -> stringResource(R.string.connection_status_verifying)
        }

        is ObdConnectionState.Failed -> stringResource(R.string.connection_status_failed)
    }

private fun ObdConnectionState.statusColor(): Color =
    when (this) {
        is ObdConnectionState.Connected -> when (ecuStatus) {
            EcuStatus.VERIFIED -> HomeGreen
            EcuStatus.NO_COMMUNICATION -> HomeOrange
            EcuStatus.INVALID_RESPONSE -> HomeRed
            EcuStatus.VERIFYING, EcuStatus.UNKNOWN -> HomeOrange
        }

        is ObdConnectionState.Failed -> HomeRed
        is ObdConnectionState.Connecting, is ObdConnectionState.Initializing -> HomeBlue
        is ObdConnectionState.Disconnected -> HomeTextSecondary
    }

private fun ObdConnectionState.configOrNull(): ObdWifiConfig? =
    when (this) {
        is ObdConnectionState.Connected -> config
        is ObdConnectionState.Initializing -> config
        is ObdConnectionState.Connecting -> config
        is ObdConnectionState.Failed -> config
        is ObdConnectionState.Disconnected -> null
    }
