package com.app.drivedata.obd

import java.io.InputStream

/**
 * Counts ELM327 prompts (`>`) read from the adapter.
 *
 * `ObdDeviceConnection` consumes the prompt before returning the raw response, so this wrapper is
 * the only reliable way to distinguish "the adapter answered" from "the socket opened but no
 * ELM327 is on the other side".
 */
internal class PromptTrackingInputStream(private val delegate: InputStream) : InputStream() {

    @Volatile
    var promptCount: Int = 0
        private set

    override fun read(): Int {
        val value = delegate.read()
        if (value == PROMPT) promptCount++
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(buffer, offset, length)
        if (read > 0) {
            for (index in offset until offset + read) {
                if (buffer[index] == PROMPT.toByte()) promptCount++
            }
        }
        return read
    }

    override fun skip(byteCount: Long): Long = delegate.skip(byteCount)

    override fun available(): Int = delegate.available()

    override fun close() = delegate.close()

    private companion object {
        const val PROMPT = '>'.code
    }
}
