package com.app.drivedata.obd

import com.app.drivedata.obd.model.ConnectionFailure
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.obd.transport.ObdTransport
import com.app.drivedata.obd.transport.WifiObdTransport
import com.github.eltonvs.obd.command.BadResponseException
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Owns the active adapter link and exposes its state.
 *
 * One instance is shared for the whole app (see `AppContainer`). Only one physical connection
 * is ever open at a time: [connect] tears down any previous link before opening a new one, and
 * the underlying [ObdDeviceConnection] serializes command execution internally.
 */
class ObdConnectionManager(
    private val transportFactory: (ObdWifiConfig) -> ObdTransport = { WifiObdTransport(it.host, it.port) },
) {
    private val lifecycleLock = Mutex()

    private val _state = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
    val state: StateFlow<ObdConnectionState> = _state.asStateFlow()

    private var transport: ObdTransport? = null
    private var connection: ObdDeviceConnection? = null

    /** Opens (or re-opens) the adapter link, initializes the ELM327, and verifies ECU comms. */
    suspend fun connect(config: ObdWifiConfig) =
        lifecycleLock.withLock {
            closeInternal()

            if (!config.isValid) {
                _state.value = ObdConnectionState.Failed(config, ConnectionFailure.INVALID_CONFIGURATION)
                return@withLock
            }

            _state.value = ObdConnectionState.Connecting(config)
            withContext(Dispatchers.IO) {
                val activeTransport = transportFactory(config)
                try {
                    val streams = activeTransport.open()
                    transport = activeTransport

                    val activeConnection = ObdDeviceConnection(streams.input, streams.output)
                    connection = activeConnection

                    _state.value = ObdConnectionState.Initializing(config)
                    initializeAdapter(activeConnection)
                    val protocol = describeProtocol(activeConnection)
                    val ecuStatus = verifyEcuCommunication(activeConnection)

                    _state.value = ObdConnectionState.Connected(config, protocol, ecuStatus)
                } catch (_: BadResponseException) {
                    closeInternal()
                    _state.value = ObdConnectionState.Failed(config, ConnectionFailure.ADAPTER_INITIALIZATION)
                } catch (_: IOException) {
                    closeInternal()
                    _state.value = ObdConnectionState.Failed(config, ConnectionFailure.TRANSPORT)
                } catch (_: Exception) {
                    closeInternal()
                    _state.value = ObdConnectionState.Failed(config, ConnectionFailure.UNKNOWN)
                }
            }
        }

    /** Closes the link and returns to [ObdConnectionState.Disconnected]. */
    suspend fun disconnect() =
        lifecycleLock.withLock {
            closeInternal()
            _state.value = ObdConnectionState.Disconnected
        }

    /**
     * Runs a single command against the active connection.
     *
     * Returns null when there is no open connection. Held under the lifecycle lock so the link
     * cannot be closed while a command is in flight.
     */
    suspend fun runCommand(command: ObdCommand): ObdResponse? =
        lifecycleLock.withLock { connection?.run(command) }

    private fun closeInternal() {
        connection = null
        transport?.close()
        transport = null
    }
}
