package com.app.drivedata.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.app.drivedata.R
import com.app.drivedata.sync.SyncError

/** User-facing message for a sync failure, shared by every screen that reports sync state. */
@Composable
fun SyncError.syncErrorMessage(): String =
    stringResource(
        when (this) {
            SyncError.NETWORK -> R.string.home_sync_error_network
            SyncError.AUTH -> R.string.home_sync_error_auth
            SyncError.SERVER -> R.string.home_sync_error_server
            SyncError.UNKNOWN -> R.string.home_sync_error_unknown
        },
    )

/** Human-readable last-sync timestamp shared by the dashboard and offline screens. */
@Composable
fun lastSyncText(millis: Long?): String {
    if (millis == null) return stringResource(R.string.home_sync_never)
    val time = formatClockTime(millis)
    return if (isSameDay(millis)) {
        stringResource(R.string.home_sync_today, time)
    } else {
        formatDateTime(millis)
    }
}
