package com.app.drivedata.obd

import com.app.drivedata.logging.AppLog
import com.app.drivedata.obd.model.ConnectionFailure
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.obd.transport.ObdTransport
import com.app.drivedata.obd.transport.WifiNetworkLookup
import com.app.drivedata.obd.transport.WifiObdTransport
import com.github.eltonvs.obd.command.BadResponseException
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class ObdConnectionManager(
    wifiNetworkLookup: WifiNetworkLookup? = null,
    private val transportFactory: (ObdWifiConfig) -> ObdTransport = { config ->
        WifiObdTransport(
            host = config.host,
            port = config.port,
            networkProvider = { wifiNetworkLookup?.activeWifiNetwork() },
        )
    },
) {
    private val lifecycleLock = Mutex()

    private val intentionallyClosing = AtomicBoolean(false)

    private val _state = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
    val state: StateFlow<ObdConnectionState> = _state.asStateFlow()

    @Volatile
    private var transport: ObdTransport? = null
    private var connection: ObdDeviceConnection? = null

    suspend fun connect(config: ObdWifiConfig) =
        lifecycleLock.withLock {
            closeInternal()

            if (!config.isValid) {
                AppLog.w("OBD connection skipped: invalid config (host='${config.host}', port=${config.port})")
                _state.value = ObdConnectionState.Failed(config, ConnectionFailure.INVALID_CONFIGURATION)
                return@withLock
            }

            AppLog.i("OBD connecting to ${config.host}:${config.port}")
            _state.value = ObdConnectionState.Connecting(config)
            try {
                withContext(Dispatchers.IO) {
                    val activeTransport = transportFactory(config)
                    try {
                        val streams = activeTransport.open()
                        transport = activeTransport
                        AppLog.d("OBD socket open, initializing ELM327")

                        val promptTracker = PromptTrackingInputStream(streams.input)
                        val activeConnection = ObdDeviceConnection(promptTracker, streams.output)
                        connection = activeConnection

                        _state.value = ObdConnectionState.Initializing(config)
                        initializeAdapter(activeConnection, promptTracker)
                        val protocol = describeProtocol(activeConnection)
                        val ecuStatus = verifyEcuCommunication(activeConnection)

                        AppLog.i("OBD connected: protocol=${protocol ?: "unknown"}, ecu=$ecuStatus")
                        _state.value = ObdConnectionState.Connected(config, protocol, ecuStatus)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: MissingPromptException) {
                        AppLog.e("OBD adapter did not send the expected ELM327 prompt", error)
                        closeInternal()
                        _state.value = ObdConnectionState.Failed(config, ConnectionFailure.MISSING_PROMPT)
                    } catch (error: SocketTimeoutException) {
                        AppLog.e("OBD connection timed out", error)
                        closeInternal()
                        _state.value = ObdConnectionState.Failed(config, ConnectionFailure.CONNECT_TIMEOUT)
                    } catch (error: BadResponseException) {
                        AppLog.e("OBD adapter rejected the initialization commands", error)
                        closeInternal()
                        _state.value = ObdConnectionState.Failed(config, ConnectionFailure.ADAPTER_INITIALIZATION)
                    } catch (error: IOException) {
                        AppLog.e("OBD transport failed while connecting", error)
                        closeInternal()
                        _state.value = ObdConnectionState.Failed(config, ConnectionFailure.TRANSPORT)
                    } catch (error: Exception) {
                        AppLog.e("OBD connection failed for an unknown reason", error)
                        closeInternal()
                        _state.value = ObdConnectionState.Failed(config, ConnectionFailure.UNKNOWN)
                    }
                }
            } catch (error: CancellationException) {
                AppLog.w("OBD connection cancelled")
                closeInternal()
                _state.value = ObdConnectionState.Disconnected
                throw error
            }
        }

    suspend fun disconnect() {
        AppLog.i("OBD disconnecting")
        intentionallyClosing.set(true)
        transport?.close()
        try {
            lifecycleLock.withLock {
                closeInternal()
                _state.value = ObdConnectionState.Disconnected
            }
        } finally {
            intentionallyClosing.set(false)
        }
    }

    suspend fun runCommand(command: ObdCommand): ObdResponse? =
        lifecycleLock.withLock { runCommandLocked(command) }

    /**
     * Runs [block] while holding the session lock, so a discovery sequence cannot interleave with
     * the 1 Hz metrics poll.
     *
     * The block must issue commands through the supplied runner; calling [runCommand] or
     * [disconnect] from inside would try to re-acquire the non-reentrant lock.
     */
    suspend fun <T> withExclusiveSession(
        block: suspend (runner: suspend (ObdCommand) -> ObdResponse?) -> T,
    ): T = lifecycleLock.withLock { block { command -> runCommandLocked(command) } }

    suspend fun reportEcuNoResponse() =
        lifecycleLock.withLock {
            val current = _state.value
            val alreadyReported =
                current is ObdConnectionState.Connected &&
                    (current.ecuStatus == EcuStatus.NO_COMMUNICATION ||
                        current.ecuStatus == EcuStatus.INVALID_RESPONSE)
            if (current is ObdConnectionState.Connected && !alreadyReported) {
                AppLog.w("OBD ECU stopped answering while collecting metrics")
                closeInternal()
                _state.value = ObdConnectionState.Failed(current.config, ConnectionFailure.ECU_NO_RESPONSE)
            }
        }

    private suspend fun runCommandLocked(command: ObdCommand): ObdResponse? {
        val active =
            connection ?: run {
                AppLog.w("OBD command '${command.rawCommand}' skipped: no active connection")
                return null
            }
        return try {
            active.runWithSearchingRetry(command).also { response ->
                AppLog.d(
                    "OBD '${command.rawCommand}' -> raw='${AppLog.raw(response.rawResponse.value)}' " +
                        "value='${response.formattedValue}'",
                )
            }
        } catch (e: IOException) {
            AppLog.e("OBD command '${command.rawCommand}' failed with an I/O error", e)
            if (!intentionallyClosing.get()) {
                markTransportFailure()
            }
            throw e
        }
    }

    private fun markTransportFailure() {
        val config =
            when (val current = _state.value) {
                is ObdConnectionState.Connected -> current.config
                is ObdConnectionState.Initializing -> current.config
                is ObdConnectionState.Connecting -> current.config
                else -> null
            }
        AppLog.w("OBD transport marked as failed (host='${config?.host}', port=${config?.port})")
        closeInternal()
        _state.value = ObdConnectionState.Failed(config, ConnectionFailure.TRANSPORT)
    }

    private fun closeInternal() {
        connection = null
        transport?.close()
        transport = null
    }
}
