package com.app.drivedata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.drivedata.ui.connection.ConnectionScreen
import com.app.drivedata.ui.connection.ConnectionViewModel
import com.app.drivedata.ui.home.HomeScreen
import com.app.drivedata.ui.home.HomeViewModel
import com.app.drivedata.ui.login.LoginScreen
import com.app.drivedata.ui.login.LoginViewModel
import com.app.drivedata.ui.metrics.MetricsScreen
import com.app.drivedata.ui.metrics.MetricsViewModel
import com.app.drivedata.ui.theme.DriveDataTheme
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.vehicles.VehicleListScreen
import com.app.drivedata.ui.vehicles.VehicleListViewModel
import com.app.drivedata.ui.vehicles.add.AddVehicleScreen
import com.app.drivedata.ui.vehicles.add.AddVehicleViewModel
import com.app.drivedata.ui.vehicles.details.VehicleDetailsScreen
import com.app.drivedata.ui.vehicles.details.VehicleDetailsViewModel
import com.app.drivedata.ui.vehicles.edit.EditVehicleScreen
import com.app.drivedata.ui.vehicles.edit.EditVehicleViewModel
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveDataTheme {
                DriveDataApp()
            }
        }
    }
}

private enum class AppDestination(val labelRes: Int) {
    HOME(R.string.tab_home),
    VEHICLES(R.string.tab_vehicles),
    CONNECTION(R.string.tab_connection),
    METRICS(R.string.tab_metrics),
}

private enum class VehicleRoute { ADD, DETAILS, EDIT }

@Composable
private fun DriveDataApp() {
    val application = LocalContext.current.applicationContext as DriveDataApplication
    val authSession = application.container.authSession
    val signedInUser by authSession.signedInUser.collectAsStateWithLifecycle()

    if (signedInUser == null) {
        val loginViewModel: LoginViewModel =
            viewModel(factory = LoginViewModel.factory(application))
        LoginScreen(viewModel = loginViewModel)
        return
    }

    val homeViewModel: HomeViewModel =
        viewModel(factory = HomeViewModel.factory(application))
    val connectionViewModel: ConnectionViewModel =
        viewModel(factory = ConnectionViewModel.factory(application))
    val metricsViewModel: MetricsViewModel =
        viewModel(factory = MetricsViewModel.factory(application))
    val vehicleListViewModel: VehicleListViewModel =
        viewModel(factory = VehicleListViewModel.factory(application))

    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var vehicleRoute by rememberSaveable { mutableStateOf<VehicleRoute?>(null) }
    var selectedVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var addVehicleSession by rememberSaveable { mutableStateOf(0) }
    var editReturnsToDetails by rememberSaveable { mutableStateOf(true) }
    val addVehicleSessionKey = rememberSaveable { UUID.randomUUID().toString() }
    val addVehicleViewModel: AddVehicleViewModel? =
        if (vehicleRoute == VehicleRoute.ADD) {
            viewModel(
                key = "add-vehicle-$addVehicleSessionKey-$addVehicleSession",
                factory = AddVehicleViewModel.factory(application),
            )
        } else {
            null
        }
    val detailsVehicleId = selectedVehicleId
    val vehicleDetailsViewModel: VehicleDetailsViewModel? =
        if (vehicleRoute == VehicleRoute.DETAILS && detailsVehicleId != null) {
            viewModel(
                key = "vehicle-details-$detailsVehicleId",
                factory = VehicleDetailsViewModel.factory(application, detailsVehicleId),
            )
        } else {
            null
        }
    val editVehicleViewModel: EditVehicleViewModel? =
        if (vehicleRoute == VehicleRoute.EDIT && detailsVehicleId != null) {
            viewModel(
                key = "vehicle-edit-$detailsVehicleId",
                factory = EditVehicleViewModel.factory(application, detailsVehicleId),
            )
        } else {
            null
        }

    BackHandler(
        enabled =
            vehicleRoute == VehicleRoute.DETAILS ||
                vehicleRoute == VehicleRoute.EDIT,
    ) {
        when (vehicleRoute) {
            VehicleRoute.DETAILS -> {
                vehicleDetailsViewModel?.onScreenDisposed()
                selectedVehicleId = null
                vehicleRoute = null
                destination = AppDestination.VEHICLES
            }

            VehicleRoute.EDIT ->
                if (editReturnsToDetails) {
                    vehicleRoute = VehicleRoute.DETAILS
                } else {
                    selectedVehicleId = null
                    vehicleRoute = null
                    destination = AppDestination.VEHICLES
                }

            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HomeBackground,
        bottomBar = {
            AppBottomBar(
                selected = if (vehicleRoute == null) destination else null,
                onSelect = { target ->
                    addVehicleViewModel?.onScreenDisposed()
                    vehicleDetailsViewModel?.onScreenDisposed()
                    selectedVehicleId = null
                    vehicleRoute = null
                    destination = target
                },
            )
        },
    ) { innerPadding ->
        val routedModifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        when (vehicleRoute) {
            VehicleRoute.ADD ->
                addVehicleViewModel?.let { viewModel ->
                    AddVehicleScreen(
                        viewModel = viewModel,
                        onExit = { vehicleRoute = null },
                        onSaved = { vehicleRoute = null },
                        modifier = routedModifier,
                    )
                }

            VehicleRoute.DETAILS ->
                vehicleDetailsViewModel?.let { viewModel ->
                    VehicleDetailsScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.onScreenDisposed()
                            selectedVehicleId = null
                            vehicleRoute = null
                            destination = AppDestination.VEHICLES
                        },
                        onEdit = { vehicleId ->
                            editReturnsToDetails = true
                            selectedVehicleId = vehicleId
                            vehicleRoute = VehicleRoute.EDIT
                        },
                        onDeleted = {
                            selectedVehicleId = null
                            vehicleRoute = null
                            destination = AppDestination.VEHICLES
                        },
                        modifier = routedModifier,
                    )
                }

            VehicleRoute.EDIT ->
                editVehicleViewModel?.let { viewModel ->
                    EditVehicleScreen(
                        viewModel = viewModel,
                        onBack = {
                            vehicleRoute = if (editReturnsToDetails) VehicleRoute.DETAILS else null
                            if (!editReturnsToDetails) selectedVehicleId = null
                        },
                        onSaved = {
                            vehicleRoute = if (editReturnsToDetails) VehicleRoute.DETAILS else null
                            if (!editReturnsToDetails) selectedVehicleId = null
                        },
                        modifier = routedModifier,
                    )
                }

            null ->
                when (destination) {
                    AppDestination.HOME ->
                        HomeScreen(
                            viewModel = homeViewModel,
                            onSeeAllReadings = { destination = AppDestination.METRICS },
                            onOpenVehicles = { destination = AppDestination.VEHICLES },
                            onSelectVehicle = homeViewModel::selectVehicle,
                            onSignOut = authSession::signOut,
                            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                        )

                    AppDestination.VEHICLES ->
                        VehicleListScreen(
                            viewModel = vehicleListViewModel,
                            onBack = { destination = AppDestination.HOME },
                            onAddVehicle = {
                                addVehicleSession += 1
                                vehicleRoute = VehicleRoute.ADD
                            },
                            onOpenVehicle = { vehicleId ->
                                selectedVehicleId = vehicleId
                                vehicleRoute = VehicleRoute.DETAILS
                            },
                            onEditVehicle = { vehicleId ->
                                editReturnsToDetails = false
                                selectedVehicleId = vehicleId
                                vehicleRoute = VehicleRoute.EDIT
                            },
                            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                        )

                    AppDestination.CONNECTION ->
                        ConnectionScreen(
                            viewModel = connectionViewModel,
                            onBack = { destination = AppDestination.HOME },
                            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                        )

                    AppDestination.METRICS ->
                        MetricsScreen(
                            viewModel = metricsViewModel,
                            onBack = { destination = AppDestination.HOME },
                            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                        )
                }
        }
    }
}

@Composable
private fun AppBottomBar(
    selected: AppDestination?,
    onSelect: (AppDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HomeSurface),
    ) {
        HorizontalDivider(thickness = 1.dp, color = HomeDivider)
        NavigationBar(
            containerColor = HomeSurface,
            tonalElevation = 0.dp,
        ) {
            AppDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = navIcon(destination, isSelected),
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = HomeBlue,
                        selectedTextColor = HomeBlue,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = HomeTextSecondary,
                        unselectedTextColor = HomeTextSecondary,
                    ),
                )
            }
        }
    }
}

private fun navIcon(destination: AppDestination, selected: Boolean): ImageVector =
    when (destination) {
        AppDestination.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        AppDestination.VEHICLES ->
            if (selected) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar
        AppDestination.CONNECTION -> if (selected) Icons.Filled.Wifi else Icons.Outlined.Wifi
        AppDestination.METRICS -> if (selected) Icons.Filled.BarChart else Icons.Outlined.BarChart
    }
