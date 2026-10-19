package com.app.drivedata.obd.transport

import java.io.InputStream
import java.io.OutputStream

data class ObdStreams(
    val input: InputStream,
    val output: OutputStream,
)

interface ObdTransport {
    fun open(): ObdStreams

    fun close()
}
