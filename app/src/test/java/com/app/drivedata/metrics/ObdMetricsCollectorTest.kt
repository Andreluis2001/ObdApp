package com.app.drivedata.metrics

import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.ObdConnectionManager
import com.app.drivedata.obd.model.ConnectionFailure
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.obd.transport.ObdStreams
import com.app.drivedata.obd.transport.ObdTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Drives [ObdMetricsCollector] against an in-memory transport that replays the exact byte sequence
 * an ELM327 would send, so the sequential sweep can be tested without hardware.
 */
class ObdMetricsCollectorTest {

    @Test
    fun stream_readsAllMetricsSequentially_andParsesValues() =
        runBlocking {
            val transport = FakeObdTransport(connectScript() + collectScript())
            val manager = ObdConnectionManager(transportFactory = { transport })
            manager.connect(CONFIG)

            val snapshot = manager.toCollector().stream().first()

            assertEquals("93YBSR7RHEJ298116", snapshot.vin)
            assertEquals(8, snapshot.readings.size)
            assertAvailable(snapshot, MetricId.RPM, 1065.0)
            assertAvailable(snapshot, MetricId.SPEED, 64.0)
            assertAvailable(snapshot, MetricId.COOLANT_TEMPERATURE, 83.0)
            assertAvailable(snapshot, MetricId.ENGINE_LOAD, 50.2)
            assertAvailable(snapshot, MetricId.THROTTLE_POSITION, 0.0)
            assertAvailable(snapshot, MetricId.INTAKE_MANIFOLD_PRESSURE, 100.0)
            assertAvailable(snapshot, MetricId.MAF, 0.0)
            assertAvailable(snapshot, MetricId.FUEL_LEVEL, 50.2)
        }

    @Test
    fun stream_reportsUnavailable_whenEcuAnswersNoData() =
        runBlocking {
            val transport =
                FakeObdTransport(
                    connectScript() +
                        elmScript(
                            VIN_RESPONSE,
                            "41 0C 10 A4", // RPM
                            "41 0D 40", // Speed
                            "NO DATA", // Coolant temperature is unsupported on this vehicle
                            "41 04 80",
                            "41 11 00",
                            "41 0B 64",
                            "41 10 00 00",
                            "41 2F 80",
                        ),
                )
            val manager = ObdConnectionManager(transportFactory = { transport })
            manager.connect(CONFIG)

            val snapshot = manager.toCollector().stream().first()

            val coolant = snapshot.readingFor(MetricId.COOLANT_TEMPERATURE)
            assertEquals(ReadingValue.Unavailable, coolant.value)
            // A real zero (throttle closed) must stay Available, never confused with Unavailable.
            assertAvailable(snapshot, MetricId.THROTTLE_POSITION, 0.0)
        }

    @Test
    fun stream_leavesVinNull_whenVinIsUnsupported() =
        runBlocking {
            val transport =
                FakeObdTransport(
                    connectScript() +
                        elmScript(
                            "NO DATA", // VIN
                            "41 0C 10 A4",
                            "41 0D 40",
                            "41 05 7B",
                            "41 04 80",
                            "41 11 00",
                            "41 0B 64",
                            "41 10 00 00",
                            "41 2F 80",
                        ),
                )
            val manager = ObdConnectionManager(transportFactory = { transport })
            manager.connect(CONFIG)

            val snapshot = manager.toCollector().stream().first()

            assertNull(snapshot.vin)
        }

    @Test
    fun stream_completesWithoutEmitting_whenNotConnected() =
        runBlocking {
            val manager = ObdConnectionManager(transportFactory = { FakeObdTransport("") })

            val snapshots = manager.toCollector().stream().toList()

            assertTrue("expected no emissions, got $snapshots", snapshots.isEmpty())
        }

    @Test
    fun stream_failsAndReportsTransportFailure_whenLinkBreaks() =
        runBlocking {
            val transport = FakeObdTransport(connectScript(), failWhenExhausted = true)
            val manager = ObdConnectionManager(transportFactory = { transport })
            manager.connect(CONFIG)
            assertTrue("setup failed: ${manager.state.value}", manager.state.value is ObdConnectionState.Connected)

            val failure = runCatching { manager.toCollector().stream().first() }.exceptionOrNull()

            assertTrue("expected IOException but was $failure", failure is IOException)
            val state = manager.state.value
            assertTrue("expected Failed but was $state", state is ObdConnectionState.Failed)
            assertEquals(ConnectionFailure.TRANSPORT, (state as ObdConnectionState.Failed).reason)
        }

    private fun ObdConnectionManager.toCollector() = ObdMetricsCollector(this)

    private fun assertAvailable(
        snapshot: MetricsSnapshot,
        id: MetricId,
        expected: Double,
    ) {
        val value = snapshot.readingFor(id).value
        assertTrue("expected $id to be Available but was $value", value is ReadingValue.Available)
        assertEquals(expected, (value as ReadingValue.Available).value, 0.05)
    }

    private fun MetricsSnapshot.readingFor(id: MetricId) =
        readings.first { it.id == id }

    private fun connectScript(): String =
        elmScript(
            // ELM327 setup: reset, echo, line feed, spaces, headers, adaptive timing, protocol.
            "OK", "OK", "OK", "OK", "OK", "OK", "OK",
            "ISO 15765-4 (CAN 11/500)",
            "41 00 BE 3E B8 13",
            "41 0C 10 A4",
        )

    private fun collectScript(): String =
        elmScript(
            VIN_RESPONSE,
            "41 0C 10 A4", // RPM -> 1065
            "41 0D 40", // Speed -> 64 km/h
            "41 05 7B", // Coolant -> 83 °C
            "41 04 80", // Engine load -> 50.2 %
            "41 11 00", // Throttle -> 0 %
            "41 0B 64", // Intake manifold pressure -> 100 kPa
            "41 10 00 00", // MAF -> 0 g/s
            "41 2F 80", // Fuel level -> 50.2 %
        )

    private companion object {
        val CONFIG = ObdWifiConfig(host = "192.168.0.10", port = 35000)

        /** ISO9141-2/KWP format; parses to `93YBSR7RHEJ298116` per the library's own tests. */
        const val VIN_RESPONSE =
            "490201000000394902023359425349020352375248490204454A323949020538313136"
    }
}

/** Builds the byte sequence an ELM327 emits: each response followed by CR CR and the `>` prompt. */
private fun elmScript(vararg responses: String): String =
    responses.joinToString(separator = "") { "$it\r\r>" }

private class FakeObdTransport(
    script: String,
    private val onOpen: () -> Unit = {},
    failWhenExhausted: Boolean = false,
) : ObdTransport {
    private val input =
        if (failWhenExhausted) {
            ClosedSocketInputStream(script.toByteArray(Charsets.US_ASCII))
        } else {
            ByteArrayInputStream(script.toByteArray(Charsets.US_ASCII))
        }
    private val output = ByteArrayOutputStream()

    override fun open(): ObdStreams {
        onOpen()
        return ObdStreams(input, output)
    }

    override fun close() {
        // Nothing to release for the in-memory fake.
    }
}

/**
 * Replays a script and then fails like a socket whose peer disappeared: once the script is
 * drained, every read attempt throws.
 */
private class ClosedSocketInputStream(
    private val bytes: ByteArray,
) : InputStream() {
    private var position = 0

    private val drained: Boolean
        get() = position >= bytes.size

    override fun available(): Int {
        if (drained) throw IOException("simulated link loss")
        return bytes.size - position
    }

    override fun read(): Int {
        if (drained) throw IOException("simulated link loss")
        return bytes[position++].toInt() and 0xFF
    }
}
