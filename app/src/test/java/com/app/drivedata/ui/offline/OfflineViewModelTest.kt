package com.app.drivedata.ui.offline

import com.app.drivedata.metrics.dao.StoredRecordSummary
import com.app.drivedata.metrics.model.UploadStatus
import com.app.drivedata.storage.StorageStats
import com.app.drivedata.sync.SyncError
import com.app.drivedata.sync.SyncStatus
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
class OfflineViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exposesCountsAndEstimatedPendingBytes() =
        runTest {
            val viewModel = offlineViewModel()

            val state = viewModel.awaitState { it.pendingCount == 2 }

            assertEquals(2, state.pendingCount)
            assertEquals(1, state.rejectedCount)
            assertEquals(7, state.uploadedCount)
            assertEquals(10, state.totalCount)
            assertEquals(2L * 4L * 1024L, state.pendingBytes)
        }

    @Test
    fun exposesStorageUsageFraction() =
        runTest {
            val viewModel =
                offlineViewModel(
                    storageStats = flowOf(StorageStats(usedBytes = 250L, budgetBytes = 1_000L)),
                )

            val state = viewModel.awaitState { it.pendingCount == 2 }

            assertEquals(250L, state.storageUsedBytes)
            assertEquals(1_000L, state.storageBudgetBytes)
            assertEquals(0.25f, state.storagePercent, 0.001f)
        }

    @Test
    fun clampsStorageFractionWhenBudgetIsMissing() =
        runTest {
            val viewModel =
                offlineViewModel(storageStats = flowOf(StorageStats(usedBytes = 10L, budgetBytes = 0L)))

            val state = viewModel.awaitState { it.pendingCount == 2 }

            assertEquals(0f, state.storagePercent, 0.001f)
        }

    @Test
    fun exposesLastSyncAndSyncingState() =
        runTest {
            val viewModel =
                offlineViewModel(
                    lastSynced = flowOf(4_000L),
                    syncStatus = MutableStateFlow(
                        SyncStatus(isSyncing = true, lastError = SyncError.NETWORK),
                    ),
                )

            val state = viewModel.awaitState { it.isSyncing }

            assertEquals(4_000L, state.lastSyncedAtMillis)
            assertEquals(SyncError.NETWORK, state.syncError)
        }

    @Test
    fun exposesRecentRecordsAndInternetAvailability() =
        runTest {
            val viewModel = offlineViewModel(internetAvailable = flowOf(true))

            val state = viewModel.awaitState { it.internetAvailable }

            assertEquals(1, state.records.size)
            assertEquals("record-1", state.records.single().id)
            assertEquals(UploadStatus.PENDING, state.records.single().uploadStatus)
        }

    @Test
    fun reportsEmptyStateWithoutAnOwner() =
        runTest {
            val viewModel =
                offlineViewModel(
                    username = MutableStateFlow(null),
                    internetAvailable = flowOf(true),
                )

            val state = viewModel.awaitState { it.internetAvailable }

            assertEquals(0, state.pendingCount)
            assertEquals(0, state.totalCount)
            assertTrue(state.records.isEmpty())
            assertNull(state.lastSyncedAtMillis)
            assertFalse(state.isSyncing)
        }

    private fun offlineViewModel(
        username: MutableStateFlow<String?> = MutableStateFlow("user"),
        pendingCount: Flow<Int> = flowOf(2),
        rejectedCount: Flow<Int> = flowOf(1),
        uploadedCount: Flow<Int> = flowOf(7),
        totalCount: Flow<Int> = flowOf(10),
        lastSynced: Flow<Long?> = flowOf(4_000L),
        records: Flow<List<StoredRecordSummary>> = flowOf(listOf(storedRecord())),
        syncStatus: MutableStateFlow<SyncStatus> = MutableStateFlow(SyncStatus()),
        storageStats: Flow<StorageStats> =
            flowOf(StorageStats(usedBytes = 100L, budgetBytes = 1_000L)),
        internetAvailable: Flow<Boolean> = flowOf(false),
        recentLimit: Int = 200,
    ): OfflineViewModel =
        OfflineViewModel(
            signedInUser = username,
            pendingCount = { pendingCount },
            rejectedCount = { rejectedCount },
            uploadedCount = { uploadedCount },
            totalCount = { totalCount },
            lastSyncedAtMillis = { lastSynced },
            recentRecords = { _, _ -> records },
            syncStatus = syncStatus,
            storageStats = storageStats,
            internetAvailable = internetAvailable,
            recentLimit = recentLimit,
        )

    private suspend fun OfflineViewModel.awaitState(predicate: (OfflineUiState) -> Boolean): OfflineUiState =
        withTimeout(5_000L) { uiState.first(predicate) }

    private fun storedRecord(): StoredRecordSummary =
        StoredRecordSummary(
            id = "record-1",
            vin = "93YBSR7RHEJ298116",
            collectedAtMillis = 1_000L,
            uploadStatus = UploadStatus.PENDING,
            uploadedAtMillis = null,
            lastError = null,
            readingCount = 8,
        )
}
