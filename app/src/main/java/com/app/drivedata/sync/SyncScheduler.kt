package com.app.drivedata.sync

import android.content.Context
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    @Volatile
    private var lastAutomaticRequestAt = 0L

    /** Debounced enqueue used after snapshots are stored and when connectivity returns. */
    fun requestSync() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAutomaticRequestAt < AUTOMATIC_REQUEST_INTERVAL_MS) return
        lastAutomaticRequestAt = now
        enqueue(ExistingWorkPolicy.KEEP, DEBOUNCE_MILLIS)
    }

    /** User-triggered sync; replaces a delayed or waiting automatic request. */
    fun syncNow() {
        lastAutomaticRequestAt = SystemClock.elapsedRealtime()
        enqueue(ExistingWorkPolicy.REPLACE, delayMillis = 0L)
    }

    /**
     * Restarts the pipeline when a usable network appears, resetting any retry backoff. This matters
     * when the phone leaves the adapter's isolated Wi-Fi and joins mobile data.
     */
    fun onConnectivityAvailable() {
        lastAutomaticRequestAt = SystemClock.elapsedRealtime()
        enqueue(ExistingWorkPolicy.REPLACE, delayMillis = 0L)
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun enqueue(policy: ExistingWorkPolicy, delayMillis: Long) {
        val request: OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<TelemetrySyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "telemetry-sync"
        const val TAG = "telemetry-sync"

        private const val DEBOUNCE_MILLIS = 30_000L
        private const val AUTOMATIC_REQUEST_INTERVAL_MS = 15_000L
        private const val BACKOFF_SECONDS = 30L
    }
}
