package com.app.drivedata.obd

import com.app.drivedata.obd.model.DiscoveryFailure
import com.app.drivedata.obd.model.DiscoveryStage
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.obd.model.PidDiscoveryResult
import com.app.drivedata.obd.transport.ObdStreams
import com.app.drivedata.obd.transport.ObdTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Exercises the discovery coordinator through [ObdConnectionManager]'s real locking path, using an
 * in-memory ELM327 script for the initialization, ECU probe and supported-PIDs queries.
 */
class PidDiscoveryCoordinatorTest {

    @Test
    fun discoversInsideAnActiveSessionAndReportsEachRange() =
        runBlocking {
            val transport =
                CoordinatorFakeTransport(
                    coordinatorElmScript(
                        "OK", "OK", "OK", "OK", "OK", "OK", "OK",
                        "ISO 15765-4 (CAN 11/500)",
                        // ECU test: bitmap + RPM probe.
                        "41 00 BE 3E B8 13",
                        "41 0C 1A F8",
                        // Discovery ranges.
                        "41 00 BE 1F A8 13",
                        "41 20 80 00 00 00",
                    ),
                )
            val manager = ObdConnectionManager(transportFactory = { transport })
            manager.connect(ObdWifiConfig(host = "192.168.1.119", port = 35000))

            val progress = mutableListOf<Pair<Int, Int>>()
            val result =
                PidDiscoveryCoordinator(manager, clock = { 987L })
                    .discover { base, found -> progress += base to found }

            assertTrue(manager.state.value is ObdConnectionState.Connected)
            result as PidDiscoveryResult.Success
            assertEquals(987L, result.discoveredAtMillis)
            assertTrue(0x0C in result.pids)
            assertTrue(0x20 in result.pids)
            assertTrue(0x21 in result.pids)
            assertEquals(listOf(0x00, 0x20), progress.map { it.first })
            assertEquals(0, progress.first().second)
        }

    @Test
    fun withoutAnActiveConnection_reportsConnectionLost() =
        runBlocking {
            val manager = ObdConnectionManager(transportFactory = { CoordinatorFakeTransport("") })

            val result = PidDiscoveryCoordinator(manager).discover()

            assertEquals(
                PidDiscoveryResult.Failure(
                    DiscoveryStage.RANGE_QUERY,
                    DiscoveryFailure.CONNECTION_LOST,
                ),
                result,
            )
        }
}

private fun coordinatorElmScript(vararg responses: String): String =
    responses.joinToString(separator = "") { "$it\r\r>" }

private class CoordinatorFakeTransport(script: String) : ObdTransport {
    private val input = ByteArrayInputStream(script.toByteArray(Charsets.US_ASCII))
    private val output = ByteArrayOutputStream()

    override fun open(): ObdStreams = ObdStreams(input, output)

    override fun close() = Unit
}
