package com.app.drivedata.metrics

import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetricsCollectionStarterTest {

    @Test
    fun reusesAVerifiedLiveLinkWithoutReconnecting() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection()

            val result = harness.starter.start()

            assertEquals(CollectionStartResult.STARTED, result)
            assertTrue(harness.connectCalls.isEmpty())
        }

    @Test
    fun reconnectsWithTheSavedIpAndPortWhenTheLinkIsGone() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = ObdConnectionState.Disconnected
            harness.active.value = collection(host = "192.168.4.1")

            val result = harness.starter.start()

            assertEquals(CollectionStartResult.STARTED, result)
            assertEquals(listOf(ObdWifiConfig("192.168.4.1", PORT)), harness.connectCalls)
        }

    @Test
    fun doesNotConnectWhenTheActiveVehicleHasNoSavedAdapter() =
        runTest {
            val harness = Harness(this)
            harness.active.value = collection(host = " ")

            val result = harness.starter.start()

            assertEquals(CollectionStartResult.NO_ADAPTER, result)
            assertTrue(harness.connectCalls.isEmpty())
            assertFalse(harness.controller.state.value.isCollecting)
        }

    @Test
    fun doesNotStartWhenTheEcuIsNotVerified() =
        runTest {
            val connection = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
            val harness =
                Harness(
                    testScope = this,
                    connection = connection,
                    connect = { config ->
                        connection.value =
                            ObdConnectionState.Connected(config, "CAN", EcuStatus.NO_COMMUNICATION)
                    },
                )
            harness.active.value = collection()

            val result = harness.starter.start()

            assertEquals(CollectionStartResult.ECU_UNAVAILABLE, result)
            assertFalse(harness.controller.state.value.isCollecting)
        }

    @Test
    fun reportsBusyWhileAnotherAdapterSessionIsBeingEstablished() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = ObdConnectionState.Connecting(ObdWifiConfig(HOST, PORT))
            harness.active.value = collection()

            val result = harness.starter.start()

            assertEquals(CollectionStartResult.ADAPTER_BUSY, result)
            assertTrue(harness.connectCalls.isEmpty())
        }

    private class Harness(
        testScope: TestScope,
        connection: MutableStateFlow<ObdConnectionState> =
            MutableStateFlow(ObdConnectionState.Disconnected),
        connect: suspend (ObdWifiConfig) -> Unit = { config ->
            connection.value = ObdConnectionState.Connected(config, "CAN", EcuStatus.VERIFIED)
        },
        stream: (String?, List<MetricDefinition>) -> Flow<MetricsSnapshot> = { _, _ -> flowOf(snapshot()) },
    ) {
        private val scope =
            CoroutineScope(
                testScope.backgroundScope.coroutineContext +
                    UnconfinedTestDispatcher(testScope.testScheduler),
            )

        val connection: MutableStateFlow<ObdConnectionState> = connection
        val active = MutableStateFlow<ActiveVehicleCollection?>(null)
        val connectCalls = mutableListOf<ObdWifiConfig>()

        private val signedInUser = MutableStateFlow<String?>("admin")

        val controller =
            MetricsCollectionController(
                scope = scope,
                connectionState = connection,
                activeVehicle = active,
                streamFactory = stream,
                repository = MetricsRepository(FakeMetricRecordDao()),
                signedInUser = signedInUser,
            )

        val starter =
            MetricsCollectionStarter(
                activeVehicle = active,
                connectionState = connection,
                connect = { config ->
                    connectCalls += config
                    connect(config)
                },
                controller = controller,
            )
    }

    private companion object {
        const val HOST = "192.168.0.10"
        const val PORT = 35_000

        val rpmDefinition: MetricDefinition =
            defaultMetricCatalog.first { it.id == MetricId.RPM }

        fun collection(host: String = HOST): ActiveVehicleCollection =
            ActiveVehicleCollection(
                vehicleId = "vehicle-1",
                displayName = "Chevrolet S10",
                host = host,
                port = PORT,
                vin = "93YBSR7RHEJ298116",
                hasDiscovery = true,
                catalog = listOf(rpmDefinition),
            )

        fun connected(): ObdConnectionState.Connected =
            ObdConnectionState.Connected(ObdWifiConfig(HOST, PORT), "CAN", EcuStatus.VERIFIED)

        fun snapshot(): MetricsSnapshot =
            MetricsSnapshot(
                timestampMillis = 1_000L,
                vin = "VIN",
                readings =
                    listOf(
                        MetricReading(MetricId.RPM, "RPM", ReadingValue.Available(900.0)),
                    ),
            )
    }
}
