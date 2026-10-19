package com.app.drivedata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.ui.connection.ConnectionScreen
import com.app.drivedata.ui.connection.ConnectionViewModel
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

/** Screens reachable from the bottom navigation bar while an adapter is connected. */
private enum class AppDestination { CONNECTION, METRICS }

@Composable
private fun DriveDataApp() {
    val application = LocalContext.current.applicationContext as DriveDataApplication
    val connectionState by application.container.obdConnectionManager.state.collectAsStateWithLifecycle()

    val connectionViewModel: ConnectionViewModel =
        viewModel(factory = ConnectionViewModel.factory(application))
    val metricsViewModel: MetricsViewModel =
        viewModel(factory = MetricsViewModel.factory(application))

    var destination by rememberSaveable { mutableStateOf(AppDestination.CONNECTION) }
    val isConnected = connectionState is ObdConnectionState.Connected

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isConnected) {
                NavigationBar {
                    NavigationBarItem(
                        selected = destination == AppDestination.CONNECTION,
                        onClick = { destination = AppDestination.CONNECTION },
                        icon = {},
                        label = { Text(stringResource(R.string.tab_connection)) },
                    )
                    NavigationBarItem(
                        selected = destination == AppDestination.METRICS,
                        onClick = { destination = AppDestination.METRICS },
                        icon = {},
                        label = { Text(stringResource(R.string.tab_metrics)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        if (isConnected && destination == AppDestination.METRICS) {
            MetricsScreen(viewModel = metricsViewModel, modifier = contentModifier)
        } else {
            ConnectionScreen(viewModel = connectionViewModel, modifier = contentModifier)
        }
    }
}
