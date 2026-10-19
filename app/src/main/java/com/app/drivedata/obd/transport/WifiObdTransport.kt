package com.app.drivedata.obd.transport

import java.net.InetSocketAddress
import java.net.Socket

class WifiObdTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
) : ObdTransport {

    private var socket: Socket? = null

    override fun open(): ObdStreams {
        close()
        val newSocket =
            Socket().apply {
                tcpNoDelay = true
                soTimeout = readTimeoutMs
                connect(InetSocketAddress(host, port), connectTimeoutMs)
            }
        socket = newSocket
        return ObdStreams(newSocket.getInputStream(), newSocket.getOutputStream())
    }

    override fun close() {
        val current = socket ?: return
        socket = null
        runCatching { current.close() }
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000
        const val DEFAULT_READ_TIMEOUT_MS = 10_000
    }
}
