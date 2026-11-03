package com.app.drivedata.sync

sealed interface SyncRunResult {
    data class Success(
        val uploaded: Int,
        val rejected: Int,
        val pruned: Int,
    ) : SyncRunResult

    data class Retryable(
        val reason: String,
        val error: SyncError = SyncError.NETWORK,
    ) : SyncRunResult

    data class Failed(
        val reason: String,
        val error: SyncError = SyncError.UNKNOWN,
    ) : SyncRunResult
}
