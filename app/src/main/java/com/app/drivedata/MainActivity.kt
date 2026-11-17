package com.app.drivedata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
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
import com.app.drivedata.ui.vehicles.details.VehicleDetailsScreen
import com.app.drivedata.ui.vehicles.details.VehicleDetailsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The brand palette is light-only, so the system bars are pinned instead of
        // following the device dark-mode setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle =
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
        )
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

private enum class VehicleRoute { DETAILS }

@Composable
private fun DriveDataApp() {
    val application = LocalContext.current.applicationContext as DriveDataApplication
    val authSession = application.container.authSession
    val isReady by authSession.isReady.collectAsStateWithLifecycle()
    val signedInUser by authSession.signedInUser.collectAsStateWithLifecycle()

    if (!isReady) {
        Box(
            modifier = Modifier.fillMaxSize().background(HomeBackground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = HomeBlue)
        }
        return
    }

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
    var detailsReturnDestination by rememberSaveable { mutableStateOf(AppDestination.VEHICLES) }
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

    fun closeDetails() {
        vehicleDetailsViewModel?.onScreenDisposed()
        selectedVehicleId = null
        vehicleRoute = null
        destination = detailsReturnDestination
    }

    fun openDetails(vehicleId: String, returnTo: AppDestination) {
        detailsReturnDestination = returnTo
        selectedVehicleId = vehicleId
        vehicleRoute = VehicleRoute.DETAILS
    }

    BackHandler(enabled = vehicleRoute == VehicleRoute.DETAILS) {
        closeDetails()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HomeBackground,
        bottomBar = {
            AppBottomBar(
                selected = if (vehicleRoute == null) destination else null,
                onSelect = { target ->
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
            VehicleRoute.DETAILS ->
                vehicleDetailsViewModel?.let { viewModel ->
                    VehicleDetailsScreen(
                        viewModel = viewModel,
                        onBack = ::closeDetails,
                        onDeleted = ::closeDetails,
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
                            onOpenVehicle = { vehicleId ->
                                openDetails(vehicleId, AppDestination.VEHICLES)
                            },
                            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                        )

                    AppDestination.CONNECTION ->
                        ConnectionScreen(
                            viewModel = connectionViewModel,
                            onBack = { destination = AppDestination.HOME },
                            onOpenVehicle = { vehicleId ->
                                openDetails(vehicleId, AppDestination.CONNECTION)
                            },
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
