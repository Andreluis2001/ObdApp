package com.app.drivedata.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.obd.model.ConnectionFailure
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectionContent(
        state = state,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        modifier = modifier,
    )
}

@Composable
private fun ConnectionContent(
    state: ConnectionUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = state.connection
    val canDisconnect = connection is ObdConnectionState.Connected || connection is ObdConnectionState.Initializing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.connection_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            enabled = !state.isBusy,
            singleLine = true,
            label = { Text(stringResource(R.string.connection_host_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.port,
            onValueChange = onPortChange,
            enabled = !state.isBusy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.connection_port_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onConnect, enabled = !state.isBusy) {
                Text(stringResource(R.string.connection_connect))
            }
            OutlinedButton(onClick = onDisconnect, enabled = canDisconnect) {
                Text(stringResource(R.string.connection_disconnect))
            }
        }

        StatusCard(connection = connection)
    }
}

@Composable
private fun StatusCard(connection: ObdConnectionState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.connection_status_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(text = connection.statusText())
            if (connection is ObdConnectionState.Connected && connection.protocol != null) {
                Text(text = stringResource(R.string.connection_protocol, connection.protocol))
            }
        }
    }
}

@Composable
private fun ObdConnectionState.statusText(): String =
    when (this) {
        is ObdConnectionState.Disconnected -> stringResource(R.string.status_disconnected)
        is ObdConnectionState.Connecting -> stringResource(R.string.status_connecting)
        is ObdConnectionState.Initializing -> stringResource(R.string.status_initializing)
        is ObdConnectionState.Connected -> when (ecuStatus) {
            EcuStatus.VERIFIED -> stringResource(R.string.status_connected_verified)
            EcuStatus.NO_COMMUNICATION -> stringResource(R.string.status_connected_no_ecu)
            EcuStatus.VERIFYING, EcuStatus.UNKNOWN -> stringResource(R.string.status_initializing)
        }
        is ObdConnectionState.Failed -> stringResource(reason.messageRes())
    }

private fun ConnectionFailure.messageRes(): Int =
    when (this) {
        ConnectionFailure.INVALID_CONFIGURATION -> R.string.failure_invalid_configuration
        ConnectionFailure.TRANSPORT -> R.string.failure_transport
        ConnectionFailure.ADAPTER_INITIALIZATION -> R.string.failure_adapter_initialization
        ConnectionFailure.UNKNOWN -> R.string.failure_unknown
    }
