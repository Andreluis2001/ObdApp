package com.app.drivedata.sync

enum class SyncError {
    NETWORK,
    AUTH,
    SERVER,
    UNKNOWN,
}

data class SyncStatus(
    val isSyncing: Boolean = false,
    val lastError: SyncError? = null,
    val lastFinishedAtMillis: Long? = null,
)
