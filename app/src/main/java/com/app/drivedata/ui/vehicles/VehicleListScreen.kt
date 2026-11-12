package com.app.drivedata.ui.vehicles

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.vehicles.components.VehicleCard
import com.app.drivedata.ui.vehicles.components.VehicleHeader

@Composable
fun VehicleListScreen(
    viewModel: VehicleListViewModel,
    onBack: () -> Unit,
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingRemovalId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRemoval = state.vehicles.firstOrNull { it.id == pendingRemovalId }

    LightStatusBarIcons()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeBackground),
    ) {
        VehicleHeader(
            title = stringResource(R.string.vehicles_title),
            subtitle = stringResource(R.string.vehicles_subtitle),
            onBack = onBack,
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                VehicleErrorBanner(
                    message = error.message(),
                    onDismiss = viewModel::dismissError,
                )
            }

            when {
                state.isLoading ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = HomeBlue)
                    }

                state.vehicles.isEmpty() -> EmptyVehicles(onAddVehicle = onAddVehicle)

                else -> {
                    Button(
                        onClick = onAddVehicle,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HomeBlue),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.vehicle_add),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    state.vehicles.forEach { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            onSelectActive = { viewModel.selectActive(vehicle.id) },
                            onRemove = { pendingRemovalId = vehicle.id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    pendingRemoval?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { pendingRemovalId = null },
            title = { Text(stringResource(R.string.vehicle_remove_title)) },
            text = {
                Text(stringResource(R.string.vehicle_remove_message, vehicle.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemovalId = null
                        viewModel.delete(vehicle)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.vehicle_remove_confirm),
                        color = HomeRed,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemovalId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptyVehicles(onAddVehicle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(68.dp)
                        .background(HomeBlue.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = HomeBlue,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                text = stringResource(R.string.vehicles_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.vehicles_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onAddVehicle,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HomeBlue),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.vehicle_add))
            }
        }
    }
}

@Composable
private fun VehicleErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(HomeRed.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = HomeRed,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTextPrimary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun VehicleListError.message(): String =
    stringResource(
        when (this) {
            VehicleListError.ACTIVATE_FAILED -> R.string.vehicle_error_activate
            VehicleListError.DELETE_FAILED -> R.string.vehicle_error_delete
        },
    )
