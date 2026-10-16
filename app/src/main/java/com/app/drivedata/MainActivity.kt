package com.app.drivedata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.drivedata.ui.connection.ConnectionScreen
import com.app.drivedata.ui.connection.ConnectionViewModel
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

@Composable
private fun DriveDataApp() {
    val application = LocalContext.current.applicationContext as DriveDataApplication
    val viewModel: ConnectionViewModel = viewModel(factory = ConnectionViewModel.factory(application))

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        ConnectionScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
