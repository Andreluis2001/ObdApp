package com.app.drivedata.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.app.drivedata.R
import com.app.drivedata.metrics.VinMismatch
import com.app.drivedata.ui.theme.HomeRed

/** Warns that the adapter reported a different vehicle than the one selected for collection. */
@Composable
fun VinMismatchDialog(
    mismatch: VinMismatch?,
    onDismiss: () -> Unit,
) {
    if (mismatch == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = HomeRed,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.vin_mismatch_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text =
                    if (mismatch.reportedVin.isNullOrBlank()) {
                        stringResource(R.string.vin_mismatch_unavailable)
                    } else {
                        stringResource(
                            R.string.vin_mismatch_message,
                            mismatch.reportedVin,
                            mismatch.expectedVin,
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_understood))
            }
        },
    )
}
