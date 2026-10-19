package com.app.drivedata.obd

import com.app.drivedata.obd.model.ConnectionFailure
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
import java.util.concurrent.atomic.AtomicBoolean

class ObdConnectionManager(
    private val transportFactory: (ObdWifiConfig) -> ObdTransport = { WifiObdTransport(it.host, it.port) },
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

    suspend fun disconnect() {
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
        lifecycleLock.withLock {
            val active = connection ?: return@withLock null
            try {
                active.run(command)
            } catch (e: IOException) {
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
        closeInternal()
        _state.value = ObdConnectionState.Failed(config, ConnectionFailure.TRANSPORT)
    }

    private fun closeInternal() {
        connection = null
        transport?.close()
        transport = null
    }
}
