package com.app.drivedata.ui.offline

import com.app.drivedata.metrics.dao.StoredRecordSummary
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricRecord
import com.app.drivedata.metrics.model.ReadingValue
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

    @Test
    fun exposesSelectedRecordDetails() =
        runTest {
            val record = metricRecord("record-1")
            val viewModel =
                offlineViewModel(
                    recordById = { id -> flowOf(record.takeIf { it.id == id }) },
                )

            viewModel.selectRecord("record-1")

            val state = viewModel.awaitDetails { it is RecordDetailsUiState.Loaded }
            val loaded = state as RecordDetailsUiState.Loaded
            assertEquals("record-1", loaded.record?.id)
            assertEquals(2, loaded.record?.readings?.size)
            assertEquals(UploadStatus.PENDING, loaded.record?.uploadStatus)
        }

    @Test
    fun clearsSelectedRecordDetailsWhenClosed() =
        runTest {
            val viewModel =
                offlineViewModel(recordById = { flowOf(metricRecord("record-1")) })

            viewModel.selectRecord("record-1")
            viewModel.awaitDetails { it is RecordDetailsUiState.Loaded }
            viewModel.selectRecord(null)

            val state = viewModel.awaitDetails { it is RecordDetailsUiState.Idle }
            assertEquals(RecordDetailsUiState.Idle, state)
        }

    @Test
    fun reportsMissingRecordAsLoadedWithoutRecord() =
        runTest {
            val viewModel = offlineViewModel(recordById = { flowOf(null) })

            viewModel.selectRecord("missing")

            val state = viewModel.awaitDetails { it is RecordDetailsUiState.Loaded }
            assertNull((state as RecordDetailsUiState.Loaded).record)
        }

    private fun offlineViewModel(
        username: MutableStateFlow<String?> = MutableStateFlow("user"),
        pendingCount: Flow<Int> = flowOf(2),
        rejectedCount: Flow<Int> = flowOf(1),
        uploadedCount: Flow<Int> = flowOf(7),
        totalCount: Flow<Int> = flowOf(10),
        lastSynced: Flow<Long?> = flowOf(4_000L),
        records: Flow<List<StoredRecordSummary>> = flowOf(listOf(storedRecord())),
        recordById: (String) -> Flow<MetricRecord?> = { flowOf(null) },
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
            recordById = recordById,
            syncStatus = syncStatus,
            storageStats = storageStats,
            internetAvailable = internetAvailable,
            recentLimit = recentLimit,
        )

    private suspend fun OfflineViewModel.awaitState(predicate: (OfflineUiState) -> Boolean): OfflineUiState =
        withTimeout(5_000L) { uiState.first(predicate) }

    private suspend fun OfflineViewModel.awaitDetails(
        predicate: (RecordDetailsUiState) -> Boolean,
    ): RecordDetailsUiState = withTimeout(5_000L) { selectedRecordState.first(predicate) }

    private fun metricRecord(id: String): MetricRecord =
        MetricRecord(
            id = id,
            ownerUsername = "user",
            vin = "93YBSR7RHEJ298116",
            collectedAtMillis = 1_000L,
            readings =
                listOf(
                    MetricReading(MetricId.RPM, "rpm", ReadingValue.Available(900.0)),
                    MetricReading(MetricId.SPEED, "km/h", ReadingValue.Unavailable),
                ),
            uploadStatus = UploadStatus.PENDING,
        )

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
