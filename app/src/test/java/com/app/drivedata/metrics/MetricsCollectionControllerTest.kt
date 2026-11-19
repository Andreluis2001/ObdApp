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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetricsCollectionControllerTest {

    @Test
    fun doesNotStartWithoutActiveVehicle() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected()

            advanceUntilIdle()

            assertTrue(harness.requestedCatalogs.isEmpty())
            assertFalse(harness.controller.state.value.isCollecting)
        }

    @Test
    fun doesNotStartWithoutDiscovery() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection(hasDiscovery = false, catalog = emptyList())

            advanceUntilIdle()

            assertTrue(harness.requestedCatalogs.isEmpty())
        }

    @Test
    fun doesNotStartWhenEveryPidIsDisabled() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection(hasDiscovery = true, catalog = emptyList())

            advanceUntilIdle()

            assertTrue(harness.requestedCatalogs.isEmpty())
        }

    @Test
    fun doesNotStartWhenTheAdapterBelongsToAnotherVehicle() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected(host = "192.168.0.99")
            harness.active.value = collection()

            advanceUntilIdle()

            assertTrue(harness.requestedCatalogs.isEmpty())
        }

    @Test
    fun doesNotStartBeforeTheEcuIsVerified() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected(ecuStatus = EcuStatus.NO_COMMUNICATION)
            harness.active.value = collection()

            advanceUntilIdle()

            assertTrue(harness.requestedCatalogs.isEmpty())
        }

    @Test
    fun startsWithTheActiveVehicleCatalog() =
        runTest {
            val catalog = listOf(rpmDefinition)
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection(catalog = catalog)

            advanceUntilIdle()

            assertEquals(listOf(catalog), harness.requestedCatalogs)
            assertNotNull(harness.controller.state.value.snapshot)
        }

    @Test
    fun restartsWhenTheActiveVehicleChanges() =
        runTest {
            val rpmCatalog = listOf(rpmDefinition)
            val speedCatalog = listOf(speedDefinition)
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection(vehicleId = "v1", catalog = rpmCatalog)
            advanceUntilIdle()

            harness.active.value = collection(vehicleId = "v2", catalog = speedCatalog)
            advanceUntilIdle()

            assertEquals(listOf(rpmCatalog, speedCatalog), harness.requestedCatalogs)
        }

    @Test
    fun restartsWhenTheEnabledCatalogChangesForTheSameVehicle() =
        runTest {
            val rpmCatalog = listOf(rpmDefinition)
            val speedCatalog = listOf(speedDefinition)
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection(catalog = rpmCatalog)
            advanceUntilIdle()

            harness.active.value = collection(catalog = speedCatalog)
            advanceUntilIdle()

            assertEquals(listOf(rpmCatalog, speedCatalog), harness.requestedCatalogs)
        }

    @Test
    fun aUserStopPreventsRestartUntilCollectionIsStartedAgain() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection(vehicleId = "v1", catalog = listOf(rpmDefinition))
            advanceUntilIdle()
            harness.controller.stopCollection()

            harness.active.value = collection(vehicleId = "v2", catalog = listOf(speedDefinition))
            advanceUntilIdle()

            assertEquals(1, harness.requestedCatalogs.size)

            harness.controller.startCollection()
            advanceUntilIdle()

            assertEquals(2, harness.requestedCatalogs.size)
        }

    @Test
    fun maintenancePauseKeepsCollectionStoppedUntilResumed() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection(catalog = listOf(rpmDefinition))
            advanceUntilIdle()

            harness.controller.pauseForMaintenance()
            assertFalse(harness.controller.state.value.isCollecting)

            harness.controller.resumeAfterMaintenance()
            advanceUntilIdle()

            assertEquals(2, harness.requestedCatalogs.size)
        }

    @Test
    fun passesTheSelectedVehicleVinToTheStream() =
        runTest {
            val harness = Harness(this)
            harness.connection.value = connected()
            harness.active.value = collection(vin = "WP0ZZZ99ZTS390000")

            advanceUntilIdle()

            assertEquals(listOf("WP0ZZZ99ZTS390000"), harness.requestedVins)
        }

    @Test
    fun vinMismatch_stopsCollectionWarnsAndDisconnects() =
        runTest {
            val harness =
                Harness(
                    this,
                    stream = { _, _ ->
                        flow<MetricsSnapshot> { throw VinMismatchException("WP0ZZZ99ZTS390000", "MAT403096BNL00000") }
                    },
                )
            harness.connection.value = connected()
            harness.active.value = collection()

            advanceUntilIdle()

            val state = harness.controller.state.value
            assertEquals(MetricsCollectionError.VIN_MISMATCH, state.error)
            assertEquals(
                VinMismatch("WP0ZZZ99ZTS390000", "MAT403096BNL00000"),
                state.vinMismatch,
            )
            assertEquals(1, harness.disconnectCalls)
            assertFalse(state.isCollecting)

            harness.controller.dismissVinMismatch()
            assertNull(harness.controller.state.value.vinMismatch)
            assertNull(harness.controller.state.value.error)
        }

    @Test
    fun losingTheActiveVehicleClearsTheVinMismatchWarning() =
        runTest {
            val harness =
                Harness(
                    this,
                    stream = { _, _ -> flow<MetricsSnapshot> { throw VinMismatchException("WP0ZZZ99ZTS390000", null) } },
                )
            harness.connection.value = connected()
            harness.active.value = collection()
            advanceUntilIdle()
            assertNotNull(harness.controller.state.value.vinMismatch)

            harness.active.value = null
            advanceUntilIdle()

            assertNull(harness.controller.state.value.vinMismatch)
        }

    private class Harness(
        testScope: TestScope,
        stream: (String?, List<MetricDefinition>) -> Flow<MetricsSnapshot> = { _, _ -> flowOf(snapshot()) },
    ) {
        private val scope =
            CoroutineScope(
                testScope.backgroundScope.coroutineContext +
                    UnconfinedTestDispatcher(testScope.testScheduler),
            )

        val connection = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
        val active = MutableStateFlow<ActiveVehicleCollection?>(null)
        val requestedCatalogs = mutableListOf<List<MetricDefinition>>()
        val requestedVins = mutableListOf<String?>()
        var disconnectCalls = 0

        private val signedInUser = MutableStateFlow<String?>("admin")
        private val repository = MetricsRepository(FakeMetricRecordDao())

        val controller =
            MetricsCollectionController(
                scope = scope,
                connectionState = connection,
                activeVehicle = active,
                streamFactory = { vin, catalog ->
                    requestedVins += vin
                    requestedCatalogs += catalog
                    stream(vin, catalog)
                },
                repository = repository,
                signedInUser = signedInUser,
                disconnect = { disconnectCalls++ },
            )
    }

    private companion object {
        const val HOST = "192.168.0.10"
        const val PORT = 35_000
        const val VIN = "93YBSR7RHEJ298116"

        val rpmDefinition: MetricDefinition =
            defaultMetricCatalog.first { it.id == MetricId.RPM }
        val speedDefinition: MetricDefinition =
            defaultMetricCatalog.first { it.id == MetricId.SPEED }

        fun collection(
            vehicleId: String = "vehicle-1",
            host: String = HOST,
            vin: String = VIN,
            catalog: List<MetricDefinition> = listOf(rpmDefinition),
            hasDiscovery: Boolean = true,
        ): ActiveVehicleCollection =
            ActiveVehicleCollection(
                vehicleId = vehicleId,
                displayName = "Chevrolet S10",
                host = host,
                port = PORT,
                vin = vin,
                hasDiscovery = hasDiscovery,
                catalog = catalog,
            )

        fun connected(
            host: String = HOST,
            ecuStatus: EcuStatus = EcuStatus.VERIFIED,
        ): ObdConnectionState.Connected =
            ObdConnectionState.Connected(ObdWifiConfig(host, PORT), "CAN", ecuStatus)

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
