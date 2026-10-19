package com.app.drivedata.obd

import com.app.drivedata.obd.model.ConnectionFailure
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.obd.transport.ObdStreams
import com.app.drivedata.obd.transport.ObdTransport
import com.github.eltonvs.obd.command.engine.RPMCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Drives [ObdConnectionManager] against an in-memory transport whose input stream replays the
 * bytes an ELM327 would send, so the connect/initialize/verify flow can be tested without hardware.
 */
class ObdConnectionManagerTest {

    @Test
    fun connect_reportsVerifiedEcu_whenAdapterAndEcuRespond() =
        runBlocking {
            val transport =
                FakeObdTransport(
                    elmScript(
                        // ELM327 setup: reset, echo, line feed, spaces, headers, adaptive timing, protocol.
                        "OK", "OK", "OK", "OK", "OK", "OK", "OK",
                        "ISO 15765-4 (CAN 11/500)",
                        "41 00 BE 3E B8 13",
                        "41 0C 1A F8",
                    ),
                )
            val manager = ObdConnectionManager(transportFactory = { transport })

            manager.connect(ObdWifiConfig(host = "192.168.0.10", port = 35000))

            val state = manager.state.value
            assertTrue("expected Connected but was $state", state is ObdConnectionState.Connected)
            state as ObdConnectionState.Connected
            assertEquals(EcuStatus.VERIFIED, state.ecuStatus)
            assertEquals("ISO 15765-4 (CAN 11/500)", state.protocol)
        }

    @Test
    fun connect_reportsNoEcuCommunication_whenEcuAnswersNoData() =
        runBlocking {
            val transport =
                FakeObdTransport(
                    elmScript(
                        "OK", "OK", "OK", "OK", "OK", "OK", "OK",
                        "ISO 15765-4 (CAN 11/500)",
                        "NO DATA",
                    ),
                )
            val manager = ObdConnectionManager(transportFactory = { transport })

            manager.connect(ObdWifiConfig(host = "192.168.0.10", port = 35000))

            val state = manager.state.value
            assertTrue("expected Connected but was $state", state is ObdConnectionState.Connected)
            assertEquals(EcuStatus.NO_COMMUNICATION, (state as ObdConnectionState.Connected).ecuStatus)
        }

    @Test
    fun connect_failsWithoutOpeningTransport_whenConfigIsInvalid() =
        runBlocking {
            var opened = false
            val manager =
                ObdConnectionManager(
                    transportFactory = { FakeObdTransport("", onOpen = { opened = true }) },
                )

            manager.connect(ObdWifiConfig(host = " ", port = 0))

            assertEquals(false, opened)
            val state = manager.state.value
            assertTrue("expected Failed but was $state", state is ObdConnectionState.Failed)
            assertEquals(ConnectionFailure.INVALID_CONFIGURATION, (state as ObdConnectionState.Failed).reason)
        }

    @Test
    fun disconnect_closesTheLinkAndRejectsCommands() =
        runBlocking {
            val transport =
                FakeObdTransport(
                    elmScript(
                        "OK", "OK", "OK", "OK", "OK", "OK", "OK",
                        "ISO 15765-4 (CAN 11/500)",
                        "41 00 BE 3E B8 13",
                        "41 0C 1A F8",
                    ),
                )
            val manager = ObdConnectionManager(transportFactory = { transport })
            manager.connect(ObdWifiConfig(host = "192.168.0.10", port = 35000))

            manager.disconnect()

            assertTrue(manager.state.value is ObdConnectionState.Disconnected)
            assertNull(manager.runCommand(RPMCommand()))
        }
}

/** Builds the byte sequence an ELM327 emits: each response follows by CR CR and the `>` prompt. */
private fun elmScript(vararg responses: String): String =
    responses.joinToString(separator = "") { "$it\r\r>" }

private class FakeObdTransport(
    script: String,
    private val onOpen: () -> Unit = {},
) : ObdTransport {
    private val input = ByteArrayInputStream(script.toByteArray(Charsets.US_ASCII))
    private val output = ByteArrayOutputStream()

    override fun open(): ObdStreams {
        onOpen()
        return ObdStreams(input, output)
    }

    override fun close() {
        // Nothing to release for the in-memory fake.
    }
}
