package com.app.drivedata.ui.profile

import com.app.drivedata.obd.model.EcuStatus
import com.app.drivedata.obd.model.ObdConnectionState
import com.app.drivedata.obd.model.ObdWifiConfig
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun derivesNameAndEmailFromEmailUsername() =
        runTest {
            val viewModel =
                profileViewModel(username = MutableStateFlow("andre.vieira@empresa.com"))

            val state = viewModel.awaitState { it.displayName.isNotBlank() }

            assertEquals("Andre Vieira", state.displayName)
            assertEquals("andre.vieira@empresa.com", state.email)
            assertEquals("andre.vieira@empresa.com", state.username)
        }

    @Test
    fun derivesNameWithoutEmailForPlainUsername() =
        runTest {
            val viewModel = profileViewModel(username = MutableStateFlow("admin"))

            val state = viewModel.awaitState { it.username == "admin" }

            assertEquals("Admin", state.displayName)
            assertNull(state.email)
        }

    @Test
    fun exposesVehicleCount() =
        runTest {
            val viewModel =
                profileViewModel(
                    vehicles = flowOf(listOf(vehicle("v1"), vehicle("v2"))),
                )

            val state = viewModel.awaitState { it.vehicleCount == 2 }

            assertEquals(2, state.vehicleCount)
        }

    @Test
    fun exposesAdapterAndInternetStatus() =
        runTest {
            val viewModel =
                profileViewModel(
                    username = MutableStateFlow("user"),
                    connection = MutableStateFlow(connected()),
                    internetAvailable = flowOf(true),
                )

            val state = viewModel.awaitState { it.username == "user" }

            assertTrue(state.adapterConnected)
            assertTrue(state.internetAvailable)
        }

    @Test
    fun reportsOfflineWhenThereIsNoAdapterOrInternet() =
        runTest {
            val viewModel = profileViewModel(username = MutableStateFlow("user"))

            val state = viewModel.awaitState { it.username == "user" }

            assertFalse(state.adapterConnected)
            assertFalse(state.internetAvailable)
        }

    @Test
    fun exposesPendingDataAndSyncError() =
        runTest {
            val viewModel =
                profileViewModel(
                    pendingCount = flowOf(3),
                    rejectedCount = flowOf(1),
                    lastSyncedAtMillis = flowOf(4_000L),
                    syncStatus = MutableStateFlow(SyncStatus(lastError = SyncError.NETWORK)),
                )

            val state = viewModel.awaitState { it.pendingCount == 3 }

            assertEquals(1, state.rejectedCount)
            assertEquals(4_000L, state.lastSyncedAtMillis)
            assertEquals(SyncError.NETWORK, state.syncError)
        }

    @Test
    fun exposesConfiguredCollectionInterval() =
        runTest {
            val viewModel = profileViewModel(collectionIntervalSeconds = 60L)

            val state = viewModel.awaitState { it.collectionIntervalSeconds == 60L }

            assertEquals(60L, state.collectionIntervalSeconds)
        }

    @Test
    fun displayNameHandlesCommonSeparators() {
        assertEquals("Andre Vieira", displayNameFrom("andre.vieira@empresa.com"))
        assertEquals("Joao Silva", displayNameFrom("joao_silva"))
        assertEquals("Maria Souza", displayNameFrom("maria-souza"))
        assertEquals("Admin", displayNameFrom("admin"))
    }

    @Test
    fun emailIsOnlyExposedForEmailUsernames() {
        assertEquals("andre@empresa.com", emailFrom(" andre@empresa.com "))
        assertNull(emailFrom("admin"))
        assertNull(emailFrom(""))
    }

    private fun profileViewModel(
        username: MutableStateFlow<String?> = MutableStateFlow("andre.vieira@empresa.com"),
        vehicles: Flow<List<Vehicle>> = flowOf(emptyList()),
        connection: MutableStateFlow<ObdConnectionState> =
            MutableStateFlow(ObdConnectionState.Disconnected),
        pendingCount: Flow<Int> = flowOf(0),
        rejectedCount: Flow<Int> = flowOf(0),
        lastSyncedAtMillis: Flow<Long?> = flowOf(null),
        internetAvailable: Flow<Boolean> = flowOf(false),
        syncStatus: MutableStateFlow<SyncStatus> = MutableStateFlow(SyncStatus()),
        collectionIntervalSeconds: Long = 1L,
    ): ProfileViewModel =
        ProfileViewModel(
            signedInUser = username,
            vehicles = vehicles,
            connectionState = connection,
            pendingCount = pendingCount,
            rejectedCount = rejectedCount,
            lastSyncedAtMillis = lastSyncedAtMillis,
            internetAvailable = internetAvailable,
            syncStatus = syncStatus,
            collectionIntervalSeconds = collectionIntervalSeconds,
        )

    private suspend fun ProfileViewModel.awaitState(predicate: (ProfileUiState) -> Boolean): ProfileUiState =
        withTimeout(5_000L) { uiState.first(predicate) }

    private fun connected(): ObdConnectionState.Connected =
        ObdConnectionState.Connected(
            ObdWifiConfig("192.168.0.10", 35_000),
            "CAN",
            EcuStatus.VERIFIED,
        )

    private fun vehicle(id: String): Vehicle =
        Vehicle(
            id = id,
            ownerUsername = "owner",
            details = VehicleDetails(make = "Chevrolet", model = "S10"),
            connection = Elm327Connection.unconfigured(),
            capabilities = VehicleCapabilities(),
            isActive = false,
            createdAtMillis = 0L,
            updatedAtMillis = 0L,
        )
}
