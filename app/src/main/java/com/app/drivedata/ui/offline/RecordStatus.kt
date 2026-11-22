package com.app.drivedata.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.metrics.model.UploadStatus
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeOrange
import com.app.drivedata.ui.theme.HomeRed

@Composable
internal fun StatusPill(
    status: UploadStatus,
    modifier: Modifier = Modifier,
) {
    val label: String
    val color: Color
    when (status) {
        UploadStatus.PENDING -> {
            label = stringResource(R.string.offline_status_pending)
            color = HomeOrange
        }
        UploadStatus.UPLOADED -> {
            label = stringResource(R.string.offline_status_uploaded)
            color = HomeGreen
        }
        UploadStatus.REJECTED -> {
            label = stringResource(R.string.offline_status_rejected)
            color = HomeRed
        }
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
internal fun recordStatusDetail(
    status: UploadStatus,
    uploadedAtMillis: Long?,
    lastError: String?,
): String =
    when (status) {
        UploadStatus.UPLOADED ->
            stringResource(
                R.string.offline_record_uploaded_at,
                uploadedAtMillis?.let(::formatDateTime)
                    ?: stringResource(R.string.vehicle_never),
            )

        UploadStatus.PENDING -> stringResource(R.string.offline_record_waiting)

        UploadStatus.REJECTED ->
            lastError
                ?.let { stringResource(R.string.offline_record_rejected_reason, it) }
                ?: stringResource(R.string.offline_status_rejected)
    }

@Composable
internal fun statusColor(status: UploadStatus): Color =
    when (status) {
        UploadStatus.PENDING -> HomeOrange
        UploadStatus.UPLOADED -> HomeGreen
        UploadStatus.REJECTED -> HomeRed
    }

@Composable
internal fun recordVinText(vin: String?): String =
    if (vin.isNullOrBlank()) {
        stringResource(R.string.offline_record_vin_unknown)
    } else {
        stringResource(R.string.offline_record_vin, vin)
    }
