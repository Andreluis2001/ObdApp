package com.app.drivedata.ui.connection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.metrics.defaultMetricCatalog
import com.app.drivedata.obd.model.ConnectionFailure
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.ui.common.LightStatusBarIcons
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

private enum class ConnectionDialog { PAIR, PIDS, PROTOCOL }

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LightStatusBarIcons()
    ConnectionContent(
        state = state,
        onBack = onBack,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onTestCommunication = viewModel::testCommunication,
        modifier = modifier,
    )
}

@Composable
private fun ConnectionContent(
    state: ConnectionUiState,
    onBack: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTestCommunication: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeDialog by rememberSaveable { mutableStateOf<ConnectionDialog?>(null) }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AdapterCard(
                state = state,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
            )

            Text(
                text = stringResource(R.string.connection_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(
                    icon = Icons.Outlined.Wifi,
                    title = stringResource(R.string.connection_pair_new),
                    subtitle = stringResource(R.string.connection_pair_new_subtitle),
                    onClick = { activeDialog = ConnectionDialog.PAIR },
                )
                SettingRow(
                    icon = Icons.Outlined.Timer,
                    title = stringResource(R.string.connection_test),
                    subtitle = stringResource(R.string.connection_test_subtitle),
                    onClick = onTestCommunication,
                    enabled = state.isConfigValid && !state.isBusy,
                )
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
        ConnectionDialog.PAIR ->
            PairDeviceDialog(
                host = state.host,
                port = state.port,
                onHostChange = onHostChange,
                onPortChange = onPortChange,
                onDismiss = { activeDialog = null },
                onConfirm = {
                    activeDialog = null
                    onConnect()
                },
            )

        ConnectionDialog.PIDS -> SupportedPidsDialog(onDismiss = { activeDialog = null })

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
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connection = state.connection
    val statusColor = connection.statusColor()
    val actionLabel: Int
    val action: () -> Unit
    when (connection) {
        is ObdConnectionState.Connecting,
        is ObdConnectionState.Initializing,
        -> {
            actionLabel = R.string.connection_action_cancel
            action = onDisconnect
        }

        is ObdConnectionState.Connected -> {
            actionLabel = R.string.connection_disconnect
            action = onDisconnect
        }

        is ObdConnectionState.Failed -> {
            actionLabel = R.string.connection_action_retry
            action = onConnect
        }

        is ObdConnectionState.Disconnected -> {
            actionLabel = R.string.connection_connect
            action = onConnect
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(HomeBlue.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_elm327_adapter),
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(statusColor, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = connection.statusLabel(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                        )
                    }
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

            val failure = connection as? ObdConnectionState.Failed
            if (failure != null) {
                Text(
                    text = stringResource(failure.reason.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeRed,
                )
            }

            OutlinedButton(
                onClick = action,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                border = BorderStroke(1.dp, HomeBlue.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeBlue),
            ) {
                Text(
                    text = stringResource(actionLabel),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
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
private fun PairDeviceDialog(
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val portNumber = port.toIntOrNull()
    val isValid = host.isNotBlank() && portNumber != null && portNumber in 1..65535

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_pair_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.connection_pair_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.connection_host_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.connection_port_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isValid) {
                    Text(
                        text = stringResource(R.string.failure_invalid_configuration),
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeRed,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = isValid) {
                Text(stringResource(R.string.connection_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SupportedPidsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                defaultMetricCatalog.forEach { definition ->
                    val presentation = definition.id.presentation()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = presentation.icon,
                            contentDescription = null,
                            tint = presentation.tint,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(presentation.labelRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(presentation.unitRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = HomeTextSecondary,
                        )
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
private fun ProtocolDialog(
    protocol: String,
    address: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            EcuStatus.VERIFYING, EcuStatus.UNKNOWN -> stringResource(R.string.connection_status_verifying)
        }

        is ObdConnectionState.Failed -> stringResource(R.string.connection_status_failed)
    }

private fun ObdConnectionState.statusColor(): Color =
    when (this) {
        is ObdConnectionState.Connected -> when (ecuStatus) {
            EcuStatus.VERIFIED -> HomeGreen
            EcuStatus.NO_COMMUNICATION -> HomeOrange
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

private fun ConnectionFailure.messageRes(): Int =
    when (this) {
        ConnectionFailure.INVALID_CONFIGURATION -> R.string.failure_invalid_configuration
        ConnectionFailure.TRANSPORT -> R.string.failure_transport
        ConnectionFailure.ADAPTER_INITIALIZATION -> R.string.failure_adapter_initialization
        ConnectionFailure.UNKNOWN -> R.string.failure_unknown
    }
