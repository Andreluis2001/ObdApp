package com.app.drivedata.ui.vehicles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.ui.common.AppCard
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.ScreenHeader
import com.app.drivedata.ui.preview.previewVehicleListState
import com.app.drivedata.ui.theme.Dimens
import com.app.drivedata.ui.theme.DriveDataTheme
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.vehicles.components.VehicleCard
import com.app.drivedata.ui.vehicles.components.VehicleErrorBanner

@Composable
fun VehicleListScreen(
    viewModel: VehicleListViewModel,
    onBack: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LightStatusBarIcons()
    BackHandler(onBack = onBack)

    VehicleListContent(
        state = state,
        onOpenVehicle = onOpenVehicle,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

@Composable
private fun VehicleListContent(
    state: VehicleListUiState,
    onOpenVehicle: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        ScreenHeader(
            title = stringResource(R.string.vehicles_title),
            subtitle = stringResource(R.string.vehicles_subtitle),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            state.error?.let { error ->
                VehicleErrorBanner(
                    message = error.message(),
                    onDismiss = onDismissError,
                )
            }

            when {
                state.isLoading ->
                    AppCard(contentPadding = PaddingValues(vertical = 40.dp)) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = HomeBlue)
                        }
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
    AppCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            Box(
                modifier = Modifier
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
                fontWeight = FontWeight.Medium,
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

@Preview(
    showBackground = true,
    backgroundColor = 0xFFEEF3FA,
    widthDp = 360,
    heightDp = 800,
    name = "Vehicles - list",
)
@Composable
private fun VehicleListPreview() {
    DriveDataTheme {
        VehicleListContent(
            state = previewVehicleListState(),
            onOpenVehicle = {},
            onDismissError = {},
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFEEF3FA,
    widthDp = 360,
    heightDp = 800,
    name = "Vehicles - empty",
)
@Composable
private fun VehiclesEmptyPreview() {
    DriveDataTheme {
        VehicleListContent(
            state = previewVehicleListState(vehicles = emptyList()),
            onOpenVehicle = {},
            onDismissError = {},
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFEEF3FA,
    widthDp = 360,
    heightDp = 800,
    name = "Vehicles - loading and error",
)
@Composable
private fun VehiclesLoadingPreview() {
    DriveDataTheme {
        VehicleListContent(
            state = previewVehicleListState(isLoading = true, error = VehicleListError.ACTIVATE_FAILED),
            onOpenVehicle = {},
            onDismissError = {},
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFEEF3FA,
    widthDp = 320,
    heightDp = 760,
    fontScale = 1.3f,
    name = "Vehicles - small width, large text",
)
@Composable
private fun VehiclesLargeTextPreview() {
    DriveDataTheme {
        VehicleListContent(
            state = previewVehicleListState(),
            onOpenVehicle = {},
            onDismissError = {},
        )
    }
}
