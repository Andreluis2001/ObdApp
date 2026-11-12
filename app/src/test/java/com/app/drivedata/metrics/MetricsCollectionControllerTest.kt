package com.app.drivedata.metrics

import com.app.drivedata.auth.AuthSession
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private class Harness(testScope: TestScope) {
        private val scope =
            CoroutineScope(
                testScope.backgroundScope.coroutineContext +
                    UnconfinedTestDispatcher(testScope.testScheduler),
            )

        val connection = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
        val active = MutableStateFlow<ActiveVehicleCollection?>(null)
        val requestedCatalogs = mutableListOf<List<MetricDefinition>>()

        private val authSession = AuthSession().apply { signIn("admin") }
        private val repository = MetricsRepository(FakeMetricRecordDao())

        val controller =
            MetricsCollectionController(
                scope = scope,
                connectionState = connection,
                activeVehicle = active,
                streamFactory = { catalog ->
                    requestedCatalogs += catalog
                    flowOf(snapshot())
                },
                repository = repository,
                authSession = authSession,
            )
    }

    private companion object {
        const val HOST = "192.168.0.10"
        const val PORT = 35_000

        val rpmDefinition: MetricDefinition =
            defaultMetricCatalog.first { it.id == MetricId.RPM }
        val speedDefinition: MetricDefinition =
            defaultMetricCatalog.first { it.id == MetricId.SPEED }

        fun collection(
            vehicleId: String = "vehicle-1",
            host: String = HOST,
            catalog: List<MetricDefinition> = listOf(rpmDefinition),
            hasDiscovery: Boolean = true,
        ): ActiveVehicleCollection =
            ActiveVehicleCollection(
                vehicleId = vehicleId,
                displayName = "Chevrolet S10",
                host = host,
                port = PORT,
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
