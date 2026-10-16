package com.app.drivedata.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.settings.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State rendered by the connection screen. */
data class ConnectionUiState(
    val host: String = "",
    val port: String = "",
    val connection: ObdConnectionState = ObdConnectionState.Disconnected,
    val isBusy: Boolean = false,
)

class ConnectionViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val obdConnectionManager: ObdConnectionManager,
) : ViewModel() {

    private val host = MutableStateFlow("")
    private val port = MutableStateFlow("")

    val uiState: StateFlow<ConnectionUiState> =
        combine(host, port, obdConnectionManager.state) { hostValue, portValue, connection ->
            ConnectionUiState(
                host = hostValue,
                port = portValue,
                connection = connection,
                isBusy = connection is ObdConnectionState.Connecting ||
                    connection is ObdConnectionState.Initializing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ConnectionUiState(),
        )

    init {
        viewModelScope.launch {
            val saved = settingsRepository.currentWifiConfig()
            host.value = saved.host
            port.value = saved.port.toString()
        }
    }

    fun onHostChange(value: String) {
        host.value = value
    }

    fun onPortChange(value: String) {
        port.value = value.filter(Char::isDigit)
    }

    fun connect() {
        val config = ObdWifiConfig(host = host.value.trim(), port = port.value.toIntOrNull() ?: 0)
        viewModelScope.launch {
            if (config.isValid) {
                settingsRepository.saveWifiConfig(config)
            }
            obdConnectionManager.connect(config)
        }
    }

    fun disconnect() {
        viewModelScope.launch { obdConnectionManager.disconnect() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ConnectionViewModel(
                        settingsRepository = application.container.settingsRepository,
                        obdConnectionManager = application.container.obdConnectionManager,
                    )
                }
            }
    }
}
