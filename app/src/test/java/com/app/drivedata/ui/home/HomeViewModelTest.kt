package com.app.drivedata.ui.home

import com.app.drivedata.metrics.MetricsCollectionState
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricsSnapshot
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
import com.app.drivedata.storage.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun exposesLiveDashboardState() =
        runTest {
            val viewModel =
                homeViewModel(
                    connection = MutableStateFlow(connected(EcuStatus.VERIFIED)),
                    collection = MutableStateFlow(MetricsCollectionState(isCollecting = true, snapshot = snapshot(5_000L))),
                )

            val state = viewModel.awaitState { it.pendingCount == 12 && it.storageUsedBytes == 12_582_912L }

            assertTrue(state.isConnected)
            assertTrue(state.isCollecting)
            assertEquals("Chevrolet S10", state.vehicleName)
            assertEquals(4_000L, state.lastCollectedAtMillis)
            assertEquals(12L * 4_096L, state.pendingBytes)
            assertTrue(state.internetAvailable)
            assertEquals(1_073_741_824L, state.storageBudgetBytes)
            assertEquals(5_000L, state.snapshot?.timestampMillis)
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

    private fun homeViewModel(
        connection: MutableStateFlow<ObdConnectionState>,
        collection: MutableStateFlow<MetricsCollectionState>,
    ): HomeViewModel =
        HomeViewModel(
            vehicleName = "Chevrolet S10",
            connectionState = connection,
            collectionState = collection,
            pendingCount = flowOf(12),
            lastCollectedAtMillis = flowOf(4_000L),
            internetAvailable = flowOf(true),
            storageStats = flowOf(StorageStats(usedBytes = 12_582_912L, budgetBytes = 1_073_741_824L)),
        )

    private suspend fun HomeViewModel.awaitState(predicate: (HomeUiState) -> Boolean): HomeUiState =
        withTimeout(5_000L) { uiState.first(predicate) }

    private fun connected(ecuStatus: EcuStatus): ObdConnectionState.Connected =
        ObdConnectionState.Connected(ObdWifiConfig("192.168.0.10", 35_000), "CAN", ecuStatus)

    private fun snapshot(timestampMillis: Long): MetricsSnapshot =
        MetricsSnapshot(
            timestampMillis = timestampMillis,
            vin = "VIN",
            readings = listOf(MetricReading(MetricId.RPM, "RPM", ReadingValue.Available(900.0))),
        )
}
