package com.app.drivedata.ui.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.vehicles.components.VehicleCard
import com.app.drivedata.ui.vehicles.components.VehicleErrorBanner
import com.app.drivedata.ui.vehicles.components.VehicleHeader

@Composable
fun VehicleListScreen(
    viewModel: VehicleListViewModel,
    onBack: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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

                state.vehicles.isEmpty() -> EmptyVehicles()

                else ->
                    state.vehicles.forEach { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            onOpenDetails = { onOpenVehicle(vehicle.id) },
                        )
                    }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmptyVehicles() {
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
