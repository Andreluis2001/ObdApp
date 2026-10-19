package com.app.drivedata.storage

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File

data class StorageStats(
    val usedBytes: Long,
    val budgetBytes: Long,
)

class StorageStatsProvider(
    private val databaseFile: File,
    private val budgetBytes: Long = DEFAULT_BUDGET_BYTES,
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
) {

    fun observe(): Flow<StorageStats> =
        flow {
            while (currentCoroutineContext().isActive) {
                emit(current())
                delay(refreshIntervalMs)
            }
        }

    fun current(): StorageStats =
        StorageStats(
            usedBytes = DATABASE_SUFFIXES.sumOf { suffix -> File(databaseFile.path + suffix).length() },
            budgetBytes = budgetBytes,
        )

    companion object {
        const val DEFAULT_BUDGET_BYTES = 1L * 1024 * 1024 * 1024
        const val DEFAULT_REFRESH_INTERVAL_MS = 5_000L

        private val DATABASE_SUFFIXES = listOf("", "-wal", "-shm")
    }
}
