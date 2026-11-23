package com.app.drivedata.ui.offline

import androidx.annotation.StringRes
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
                ?.let {
                    stringResource(
                        R.string.offline_record_rejected_reason,
                        rejectionReasonText(it),
                    )
                }
                ?: stringResource(R.string.offline_status_rejected)
    }

/**
 * Rejections are stored as the raw reason returned by the API (or as a local engine message).
 * Keeping the raw value in the database preserves diagnostics; only presentation is localized.
 */
@StringRes
internal fun rejectionReasonRes(reason: String?): Int? {
    val normalized = reason?.trim()?.lowercase().orEmpty()
    return when {
        normalized == "vehicle_not_authorized" ->
            R.string.offline_rejection_vehicle_not_authorized

        normalized == "missing vin" -> R.string.offline_rejection_missing_vin
        normalized == "record rejected by the server" ||
            normalized.startsWith("request rejected by the server") ->
            R.string.offline_rejection_generic

        else -> null
    }
}

@Composable
internal fun rejectionReasonText(reason: String?): String {
    rejectionReasonRes(reason)?.let { return stringResource(it) }
    val code = reason?.trim().orEmpty()
    return if (code.isRejectionCode()) {
        stringResource(R.string.offline_rejection_unknown_code, code)
    } else {
        stringResource(R.string.offline_rejection_generic)
    }
}

/** Server reasons are snake_case codes; support text written for humans is shown generically. */
private fun String.isRejectionCode(): Boolean =
    isNotEmpty() && length <= 64 && all { it.isDigit() || it == '_' || it in 'a'..'z' }

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
