package com.app.drivedata.ui.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.metrics.MetricsRepository
import com.app.drivedata.metrics.dao.MetricRecordDao
import com.app.drivedata.metrics.dao.StoredRecordSummary
import com.app.drivedata.storage.StorageStats
import com.app.drivedata.sync.SyncError
import com.app.drivedata.sync.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class OfflineUiState(
    val pendingCount: Int = 0,
    val uploadedCount: Int = 0,
    val rejectedCount: Int = 0,
    val totalCount: Int = 0,
    val pendingBytes: Long = 0L,
    val storageUsedBytes: Long = 0L,
    val storageBudgetBytes: Long = 0L,
    val storagePercent: Float = 0f,
    val lastSyncedAtMillis: Long? = null,
    val isSyncing: Boolean = false,
    val syncError: SyncError? = null,
    val internetAvailable: Boolean = false,
    val records: List<StoredRecordSummary> = emptyList(),
    val recentLimit: Int = MetricRecordDao.DEFAULT_RECENT_LIMIT,
)

/**
 * Feeds the offline screen with local storage health and a bounded view of the records waiting
 * on the outbox. All reads are scoped to the signed-in owner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineViewModel(
    signedInUser: StateFlow<String?>,
    pendingCount: (String) -> Flow<Int>,
    rejectedCount: (String) -> Flow<Int>,
    uploadedCount: (String) -> Flow<Int>,
    totalCount: (String) -> Flow<Int>,
    lastSyncedAtMillis: (String) -> Flow<Long?>,
    recentRecords: (String, Int) -> Flow<List<StoredRecordSummary>>,
    syncStatus: StateFlow<SyncStatus>,
    storageStats: Flow<StorageStats>,
    internetAvailable: Flow<Boolean>,
    private val recentLimit: Int = MetricRecordDao.DEFAULT_RECENT_LIMIT,
) : ViewModel() {

    private val countState: Flow<CountState> =
        signedInUser.flatMapLatest { owner ->
            if (owner == null) {
                flowOf(CountState())
            } else {
                combine(
                    pendingCount(owner),
                    rejectedCount(owner),
                    uploadedCount(owner),
                    totalCount(owner),
                ) { pending, rejected, uploaded, total ->
                    CountState(
                        pendingCount = pending,
                        rejectedCount = rejected,
                        uploadedCount = uploaded,
                        totalCount = total,
                    )
                }
            }
        }

    private val detailsState: Flow<DetailsState> =
        signedInUser.flatMapLatest { owner ->
            if (owner == null) {
                flowOf(DetailsState())
            } else {
                combine(
                    lastSyncedAtMillis(owner),
                    recentRecords(owner, recentLimit),
                ) { lastSync, records ->
                    DetailsState(lastSyncedAtMillis = lastSync, records = records)
                }
            }
        }

    private val ownerState: Flow<OwnerState> =
        combine(countState, detailsState) { counts, details ->
            OwnerState(
                pendingCount = counts.pendingCount,
                rejectedCount = counts.rejectedCount,
                uploadedCount = counts.uploadedCount,
                totalCount = counts.totalCount,
                lastSyncedAtMillis = details.lastSyncedAtMillis,
                records = details.records,
            )
        }

    val uiState: StateFlow<OfflineUiState> =
        combine(ownerState, syncStatus, storageStats, internetAvailable) { owner, sync, storage, internet ->
            OfflineUiState(
                pendingCount = owner.pendingCount,
                uploadedCount = owner.uploadedCount,
                rejectedCount = owner.rejectedCount,
                totalCount = owner.totalCount,
                pendingBytes = owner.pendingCount * MetricsRepository.ESTIMATED_RECORD_BYTES,
                storageUsedBytes = storage.usedBytes,
                storageBudgetBytes = storage.budgetBytes,
                storagePercent = storage.usedFraction(),
                lastSyncedAtMillis = owner.lastSyncedAtMillis,
                isSyncing = sync.isSyncing,
                syncError = sync.lastError,
                internetAvailable = internet,
                records = owner.records,
                recentLimit = recentLimit,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = OfflineUiState(),
        )

    private data class CountState(
        val pendingCount: Int = 0,
        val rejectedCount: Int = 0,
        val uploadedCount: Int = 0,
        val totalCount: Int = 0,
    )

    private data class DetailsState(
        val lastSyncedAtMillis: Long? = null,
        val records: List<StoredRecordSummary> = emptyList(),
    )

    private data class OwnerState(
        val pendingCount: Int = 0,
        val rejectedCount: Int = 0,
        val uploadedCount: Int = 0,
        val totalCount: Int = 0,
        val lastSyncedAtMillis: Long? = null,
        val records: List<StoredRecordSummary> = emptyList(),
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = application.container
                    OfflineViewModel(
                        signedInUser = container.authSession.signedInUser,
                        pendingCount = { owner -> container.metricsRepository.observePendingCount(owner) },
                        rejectedCount = { owner -> container.metricsRepository.observeRejectedCount(owner) },
                        uploadedCount = { owner -> container.metricsRepository.observeUploadedCount(owner) },
                        totalCount = { owner -> container.metricsRepository.observeTotalCount(owner) },
                        lastSyncedAtMillis = { owner ->
                            container.metricsRepository.observeLastUploadedAtMillis(owner)
                        },
                        recentRecords = { owner, limit ->
                            container.metricsRepository.observeRecentRecords(owner, limit)
                        },
                        syncStatus = container.telemetrySyncEngine.status,
                        storageStats = container.storageStatsProvider.observe(),
                        internetAvailable = container.connectivityObserver.isAvailable,
                    )
                }
            }
    }
}

private fun StorageStats.usedFraction(): Float =
    if (budgetBytes <= 0L) {
        0f
    } else {
        (usedBytes.toFloat() / budgetBytes.toFloat()).coerceIn(0f, 1f)
    }
