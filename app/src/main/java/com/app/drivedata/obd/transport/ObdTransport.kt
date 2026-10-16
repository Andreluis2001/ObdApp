package com.app.drivedata.obd.transport

import java.io.InputStream
import java.io.OutputStream

/** Input and output streams exposed by an opened physical adapter link. */
data class ObdStreams(
    val input: InputStream,
    val output: OutputStream,
)

/**
 * Opens and closes the physical link to an ELM327 adapter.
 *
 * The OBD library is transport agnostic: it only needs the streams returned by [open].
 * All methods block and must be called off the main thread.
 */
interface ObdTransport {
    /** Opens the underlying connection and returns its streams. */
    fun open(): ObdStreams

    /** Closes the underlying connection. Safe to call when nothing is open. */
    fun close()
}
