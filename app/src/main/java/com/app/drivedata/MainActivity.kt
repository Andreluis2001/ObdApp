package com.app.drivedata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

private enum class AppDestination { HOME, CONNECTION, METRICS }

private enum class VehicleRoute { LIST, ADD, DETAILS, EDIT }

@OptIn(ExperimentalMaterial3Api::class)
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
            vehicleRoute == VehicleRoute.LIST ||
                vehicleRoute == VehicleRoute.DETAILS ||
                vehicleRoute == VehicleRoute.EDIT,
    ) {
        when (vehicleRoute) {
            VehicleRoute.LIST -> {
                selectedVehicleId = null
                vehicleRoute = null
            }

            VehicleRoute.DETAILS -> {
                vehicleDetailsViewModel?.onScreenDisposed()
                vehicleRoute = VehicleRoute.LIST
            }

            VehicleRoute.EDIT -> vehicleRoute = VehicleRoute.DETAILS
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (vehicleRoute == null && destination == AppDestination.HOME) {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        TextButton(onClick = authSession::signOut) {
                            Text(stringResource(R.string.action_sign_out))
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == AppDestination.HOME,
                    onClick = {
                        addVehicleViewModel?.onScreenDisposed()
                        vehicleDetailsViewModel?.onScreenDisposed()
                        selectedVehicleId = null
                        vehicleRoute = null
                        destination = AppDestination.HOME
                    },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = vehicleRoute == null && destination == AppDestination.CONNECTION,
                    onClick = {
                        addVehicleViewModel?.onScreenDisposed()
                        vehicleDetailsViewModel?.onScreenDisposed()
                        selectedVehicleId = null
                        vehicleRoute = null
                        destination = AppDestination.CONNECTION
                    },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_connection)) },
                )
                NavigationBarItem(
                    selected = vehicleRoute == null && destination == AppDestination.METRICS,
                    onClick = {
                        addVehicleViewModel?.onScreenDisposed()
                        vehicleDetailsViewModel?.onScreenDisposed()
                        selectedVehicleId = null
                        vehicleRoute = null
                        destination = AppDestination.METRICS
                    },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_metrics)) },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        val routedModifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        when (vehicleRoute) {
            VehicleRoute.LIST ->
                VehicleListScreen(
                    viewModel = vehicleListViewModel,
                    onBack = {
                        selectedVehicleId = null
                        vehicleRoute = null
                    },
                    onAddVehicle = {
                        addVehicleSession += 1
                        vehicleRoute = VehicleRoute.ADD
                    },
                    onOpenVehicle = { vehicleId ->
                        selectedVehicleId = vehicleId
                        vehicleRoute = VehicleRoute.DETAILS
                    },
                    modifier = routedModifier,
                )

            VehicleRoute.ADD ->
                addVehicleViewModel?.let { viewModel ->
                    AddVehicleScreen(
                        viewModel = viewModel,
                        onExit = { vehicleRoute = VehicleRoute.LIST },
                        onSaved = { vehicleRoute = VehicleRoute.LIST },
                        modifier = routedModifier,
                    )
                }

            VehicleRoute.DETAILS ->
                vehicleDetailsViewModel?.let { viewModel ->
                    VehicleDetailsScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.onScreenDisposed()
                            vehicleRoute = VehicleRoute.LIST
                        },
                        onEdit = { vehicleId ->
                            selectedVehicleId = vehicleId
                            vehicleRoute = VehicleRoute.EDIT
                        },
                        onDeleted = {
                            selectedVehicleId = null
                            vehicleRoute = VehicleRoute.LIST
                        },
                        modifier = routedModifier,
                    )
                }

            VehicleRoute.EDIT ->
                editVehicleViewModel?.let { viewModel ->
                    EditVehicleScreen(
                        viewModel = viewModel,
                        onBack = { vehicleRoute = VehicleRoute.DETAILS },
                        onSaved = { vehicleRoute = VehicleRoute.DETAILS },
                        modifier = routedModifier,
                    )
                }

            null ->
                when (destination) {
                    AppDestination.HOME ->
                        HomeScreen(
                            viewModel = homeViewModel,
                            onSeeAllReadings = { destination = AppDestination.METRICS },
                            onOpenVehicles = { vehicleRoute = VehicleRoute.LIST },
                            modifier = contentModifier,
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
