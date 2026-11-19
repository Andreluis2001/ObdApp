package com.app.drivedata.ui.home

import com.app.drivedata.metrics.ActiveVehicleCollection
import com.app.drivedata.metrics.MetricsCollectionState
import com.app.drivedata.metrics.defaultMetricCatalog
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.storage.StorageStats
import com.app.drivedata.sync.SyncError
import com.app.drivedata.sync.SyncStatus
import com.app.drivedata.vehicle.model.Elm327Connection
import com.app.drivedata.vehicle.model.Vehicle
import com.app.drivedata.vehicle.model.VehicleCapabilities
import com.app.drivedata.vehicle.model.VehicleDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exposesLiveDashboardAndSyncState() =
        runTest {
            val viewModel =
                homeViewModel(
                    connection = MutableStateFlow(connected(EcuStatus.VERIFIED)),
                    collection = MutableStateFlow(MetricsCollectionState(isCollecting = true, snapshot = snapshot(5_000L))),
                    syncStatus = MutableStateFlow(SyncStatus(isSyncing = true, lastError = SyncError.NETWORK)),
                )

            val state = viewModel.awaitState { it.pendingCount == 12 && it.storageUsedBytes == 12_582_912L }

            assertTrue(state.isConnected)
            assertTrue(state.isCollecting)
            assertEquals("Chevrolet S10", state.vehicleName)
            assertEquals(4_000L, state.lastSyncedAtMillis)
            assertEquals(12L * 4_096L, state.pendingBytes)
            assertTrue(state.internetAvailable)
            assertTrue(state.isSyncing)
            assertEquals(SyncError.NETWORK, state.syncError)
            assertFalse(state.canSyncNow)
            assertEquals(1_073_741_824L, state.storageBudgetBytes)
            assertEquals(5_000L, state.snapshot?.timestampMillis)
        }

    @Test
    fun exposesEmptyVehicleState_whenThereIsNoActiveVehicle() =
        runTest {
            val viewModel = homeViewModel(activeVehicle = flowOf(null))

            val state = viewModel.awaitState { it.pendingCount == 12 }

            assertFalse(state.hasVehicle)
            assertEquals("", state.vehicleName)
        }

    @Test
    fun reportsDisconnectedWhenTheEcuIsNotVerified() =
        runTest {
            val viewModel =
                homeViewModel(
                    connection = MutableStateFlow(connected(EcuStatus.NO_COMMUNICATION)),
                    collection = MutableStateFlow(MetricsCollectionState()),
                )

            val state = viewModel.awaitState { it.connection is ObdConnectionState.Connected }

            assertFalse(state.isConnected)
            assertFalse(state.isCollecting)
        }

    @Test
    fun exposesVehiclesForTheHeaderSelector() =
        runTest {
            val vehicle = vehicle("vehicle-1", active = true)
            val viewModel = homeViewModel(vehicles = flowOf(listOf(vehicle)))

            val state = viewModel.awaitState { it.vehicles.isNotEmpty() }

            assertEquals(listOf(vehicle), state.vehicles)
            assertTrue(state.vehicles.single().isActive)
        }

    @Test
    fun selectsVehicleFromTheHeaderList() =
        runTest {
            val vehicle = vehicle("vehicle-1", active = false)
            val selected = mutableListOf<Vehicle>()
            val viewModel =
                homeViewModel(
                    vehicles = flowOf(listOf(vehicle)),
                    activateVehicle = { selected += it; true },
                )

            viewModel.awaitState { it.vehicles.isNotEmpty() }
            viewModel.selectVehicle("vehicle-1")

            assertEquals(listOf(vehicle), selected)
        }

    private fun homeViewModel(
        connection: MutableStateFlow<ObdConnectionState> =
            MutableStateFlow(ObdConnectionState.Disconnected),
        collection: MutableStateFlow<MetricsCollectionState> =
            MutableStateFlow(MetricsCollectionState()),
        syncStatus: MutableStateFlow<SyncStatus> = MutableStateFlow(SyncStatus()),
        activeVehicle: Flow<ActiveVehicleCollection?> = flowOf(activeVehicle()),
        vehicles: Flow<List<Vehicle>> = flowOf(emptyList()),
        activateVehicle: suspend (Vehicle) -> Boolean = { true },
    ): HomeViewModel =
        HomeViewModel(
            activeVehicle = activeVehicle,
            vehicles = vehicles,
            connectionState = connection,
            collectionState = collection,
            readingHistory = flowOf(emptyMap()),
            pendingCount = flowOf(12),
            rejectedCount = flowOf(3),
            lastSyncedAtMillis = flowOf(4_000L),
            internetAvailable = flowOf(true),
            syncStatus = syncStatus,
            storageStats = flowOf(StorageStats(usedBytes = 12_582_912L, budgetBytes = 1_073_741_824L)),
            onSyncNow = {},
            activateVehicle = activateVehicle,
        )

    private suspend fun HomeViewModel.awaitState(predicate: (HomeUiState) -> Boolean): HomeUiState =
        withTimeout(5_000L) { uiState.first(predicate) }

    private fun connected(ecuStatus: EcuStatus): ObdConnectionState.Connected =
        ObdConnectionState.Connected(ObdWifiConfig("192.168.0.10", 35_000), "CAN", ecuStatus)

    private fun activeVehicle(): ActiveVehicleCollection =
        ActiveVehicleCollection(
            vehicleId = "vehicle-1",
            displayName = "Chevrolet S10",
            host = "192.168.0.10",
            port = 35_000,
            vin = null,
            hasDiscovery = true,
            catalog = defaultMetricCatalog,
        )

    private fun vehicle(id: String, active: Boolean): Vehicle =
        Vehicle(
            id = id,
            ownerUsername = "owner",
            details = VehicleDetails(make = "Chevrolet", model = "S10"),
            connection = Elm327Connection.unconfigured(),
            capabilities = VehicleCapabilities(),
            isActive = active,
            createdAtMillis = 0L,
            updatedAtMillis = 0L,
        )

    private fun snapshot(timestampMillis: Long): MetricsSnapshot =
        MetricsSnapshot(
            timestampMillis = timestampMillis,
            vin = "VIN",
            readings = listOf(MetricReading(MetricId.RPM, "RPM", ReadingValue.Available(900.0))),
        )
}
