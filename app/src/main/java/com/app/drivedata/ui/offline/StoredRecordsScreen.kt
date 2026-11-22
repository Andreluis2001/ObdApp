package com.app.drivedata.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.metrics.dao.StoredRecordSummary
import com.app.drivedata.metrics.model.UploadStatus
import com.app.drivedata.ui.common.ScreenHeader
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeOrange
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary

@Composable
fun StoredRecordsScreen(
    state: OfflineUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        ScreenHeader(
            title = stringResource(R.string.offline_records_title),
            subtitle = stringResource(R.string.offline_records_subtitle),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusSummaryRow(state = state)

            if (state.records.isEmpty()) {
                EmptyRecords()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = state.records, key = { it.id }) { record ->
                        RecordCard(record = record)
                    }
                    if (state.records.size >= state.recentLimit) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.offline_records_limit_note,
                                    state.recentLimit,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = HomeTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSummaryRow(state: OfflineUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusCountCard(
            label = stringResource(R.string.offline_status_pending),
            count = state.pendingCount,
            color = HomeOrange,
            modifier = Modifier.weight(1f),
        )
        StatusCountCard(
            label = stringResource(R.string.offline_status_uploaded),
            count = state.uploadedCount,
            color = HomeGreen,
            modifier = Modifier.weight(1f),
        )
        StatusCountCard(
            label = stringResource(R.string.offline_status_rejected),
            count = state.rejectedCount,
            color = HomeRed,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusCountCard(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecordCard(record: StoredRecordSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDateTime(record.collectedAtMillis),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(status = record.uploadStatus)
            }
            Text(
                text = if (record.vin.isNullOrBlank()) {
                    stringResource(R.string.offline_record_vin_unknown)
                } else {
                    stringResource(R.string.offline_record_vin, record.vin)
                },
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.offline_record_readings,
                    record.readingCount,
                    record.readingCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
            )
            Text(
                text = recordDetail(record),
                style = MaterialTheme.typography.labelSmall,
                color = detailColor(record.uploadStatus),
            )
        }
    }
}

@Composable
private fun StatusPill(status: UploadStatus) {
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
        modifier = Modifier
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
private fun recordDetail(record: StoredRecordSummary): String =
    when (record.uploadStatus) {
        UploadStatus.UPLOADED ->
            stringResource(
                R.string.offline_record_uploaded_at,
                record.uploadedAtMillis?.let(::formatDateTime)
                    ?: stringResource(R.string.vehicle_never),
            )

        UploadStatus.PENDING -> stringResource(R.string.offline_record_waiting)

        UploadStatus.REJECTED ->
            record.lastError
                ?.let { stringResource(R.string.offline_record_rejected_reason, it) }
                ?: stringResource(R.string.offline_status_rejected)
    }

@Composable
private fun detailColor(status: UploadStatus): Color =
    when (status) {
        UploadStatus.PENDING -> HomeOrange
        UploadStatus.UPLOADED -> HomeGreen
        UploadStatus.REJECTED -> HomeRed
    }

@Composable
private fun EmptyRecords() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(HomeGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Inbox,
                    contentDescription = null,
                    tint = HomeGreen,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = stringResource(R.string.offline_records_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.offline_records_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
