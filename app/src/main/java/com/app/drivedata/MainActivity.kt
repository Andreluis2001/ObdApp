package com.app.drivedata

import android.os.Bundle
import androidx.activity.ComponentActivity
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

    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = authSession::signOut) {
                        Text(stringResource(R.string.action_sign_out))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == AppDestination.HOME,
                    onClick = { destination = AppDestination.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = destination == AppDestination.CONNECTION,
                    onClick = { destination = AppDestination.CONNECTION },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_connection)) },
                )
                NavigationBarItem(
                    selected = destination == AppDestination.METRICS,
                    onClick = { destination = AppDestination.METRICS },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_metrics)) },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (destination) {
            AppDestination.HOME ->
                HomeScreen(
                    viewModel = homeViewModel,
                    onSeeAllReadings = { destination = AppDestination.METRICS },
                    modifier = contentModifier,
                )

            AppDestination.CONNECTION ->
                ConnectionScreen(viewModel = connectionViewModel, modifier = contentModifier)

            AppDestination.METRICS ->
                MetricsScreen(viewModel = metricsViewModel, modifier = contentModifier)
        }
    }
}
