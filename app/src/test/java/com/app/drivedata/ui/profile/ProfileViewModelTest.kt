package com.app.drivedata.ui.profile

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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun exposesConfiguredCollectionInterval() =
        runTest {
            val viewModel = profileViewModel(collectionInterval = flowOf(60L))

            val state = viewModel.awaitState { it.collectionIntervalSeconds == 60L }

            assertEquals(60L, state.collectionIntervalSeconds)
        }

    @Test
    fun savesAndClampsCollectionInterval() =
        runTest {
            val saved = mutableListOf<Long>()
            val viewModel = profileViewModel(saveCollectionInterval = { saved += it })

            viewModel.setCollectionIntervalSeconds(10L)
            advanceUntilIdle()
            viewModel.setCollectionIntervalSeconds(120L)
            advanceUntilIdle()
            viewModel.setCollectionIntervalSeconds(0L)
            advanceUntilIdle()

            assertEquals(listOf(10L, 60L, 1L), saved)
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
        collectionInterval: Flow<Long> = flowOf(1L),
        saveCollectionInterval: suspend (Long) -> Unit = { },
    ): ProfileViewModel =
        ProfileViewModel(
            signedInUser = username,
            vehicles = vehicles,
            collectionInterval = collectionInterval,
            saveCollectionInterval = saveCollectionInterval,
        )

    private suspend fun ProfileViewModel.awaitState(predicate: (ProfileUiState) -> Boolean): ProfileUiState =
        withTimeout(5_000L) { uiState.first(predicate) }

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
