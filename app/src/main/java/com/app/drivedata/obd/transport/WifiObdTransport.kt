package com.app.drivedata.obd.transport

import android.net.Network
import com.app.drivedata.logging.AppLog
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class WifiObdTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val networkProvider: () -> Network? = { null },
) : ObdTransport {

    private var socket: Socket? = null

    override fun open(): ObdStreams {
        close()
        val newSocket = Socket()
        AppLog.d("OBD opening TCP socket to $host:$port")
        try {
            newSocket.tcpNoDelay = true
            newSocket.soTimeout = readTimeoutMs
            bindToAdapterNetwork(newSocket)
            newSocket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        } catch (error: Throwable) {
            AppLog.e("OBD TCP socket to $host:$port failed", error)
            runCatching { newSocket.close() }
            throw error
        }
        socket = newSocket
        AppLog.i("OBD TCP socket connected: ${newSocket.localSocketAddress} -> ${newSocket.remoteSocketAddress}")
        return ObdStreams(newSocket.getInputStream(), newSocket.getOutputStream())
    }

    override fun close() {
        val current = socket ?: return
        socket = null
        runCatching { current.close() }
    }

    private fun bindToAdapterNetwork(target: Socket) {
        val network = networkProvider() ?: return
        try {
            network.bindSocket(target)
        } catch (_: IOException) {
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000
        const val DEFAULT_READ_TIMEOUT_MS = 10_000
    }
}
