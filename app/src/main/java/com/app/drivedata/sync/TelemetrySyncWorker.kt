package com.app.drivedata.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.logging.AppLog

class TelemetrySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? DriveDataApplication ?: return Result.failure()
        val container = application.container

        val owner = container.authSession.signedInUser.value ?: return Result.success()

        return when (val result = container.telemetrySyncEngine.sync(owner)) {
            is SyncRunResult.Success -> {
                AppLog.i(
                    "Telemetry sync finished: uploaded=${result.uploaded}, " +
                        "rejected=${result.rejected}, pruned=${result.pruned}",
                )
                Result.success()
            }

            is SyncRunResult.Retryable -> {
                AppLog.w("Telemetry sync will retry: ${result.reason}")
                Result.retry()
            }

            is SyncRunResult.Failed -> {
                AppLog.e("Telemetry sync stopped: ${result.reason}")
                Result.failure()
            }
        }
    }
}
