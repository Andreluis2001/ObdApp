package com.app.drivedata.ui.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.ScreenHeader
import com.app.drivedata.ui.common.SectionHeader
import com.app.drivedata.ui.common.syncErrorMessage
import com.app.drivedata.ui.common.formatBytes
import com.app.drivedata.ui.common.lastSyncText
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeOrange
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import java.util.Locale

private enum class OfflineRoute { SUMMARY, RECORDS }

@Composable
fun OfflineScreen(
    viewModel: OfflineViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LightStatusBarIcons()

    var route by rememberSaveable { mutableStateOf(OfflineRoute.SUMMARY) }
    BackHandler(enabled = route == OfflineRoute.RECORDS) {
        route = OfflineRoute.SUMMARY
    }

    when (route) {
        OfflineRoute.SUMMARY ->
            OfflineSummary(
                state = state,
                onBack = onBack,
                onOpenRecords = { route = OfflineRoute.RECORDS },
                modifier = modifier,
            )

        OfflineRoute.RECORDS ->
            StoredRecordsScreen(
                state = state,
                onBack = { route = OfflineRoute.SUMMARY },
                modifier = modifier,
            )
    }
}

@Composable
private fun OfflineSummary(
    state: OfflineUiState,
    onBack: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        ScreenHeader(
            title = stringResource(R.string.offline_title),
            subtitle = stringResource(R.string.offline_subtitle),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PendingCard(state = state)

            SyncSection(state = state)

            StorageSection(state = state)

            LastSyncCard(millis = state.lastSyncedAtMillis)

            SectionHeader(stringResource(R.string.offline_manage_title))
            RecordsRow(state = state, onClick = onOpenRecords)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PendingCard(state: OfflineUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(HomeBlue.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = HomeBlue,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = pendingCountText(state.pendingCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                Text(
                    text = stringResource(R.string.offline_pending_size, formatBytes(state.pendingBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = HomeTextPrimary,
                )
                Text(
                    text = stringResource(R.string.offline_pending_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun pendingCountText(count: Int): String =
    if (count == 0) {
        stringResource(R.string.offline_no_pending)
    } else {
        pluralStringResource(R.plurals.offline_pending_count, count, count)
    }

@Composable
private fun SyncSection(state: OfflineUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.offline_sync_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = HomeTextPrimary,
        )
        if (state.isSyncing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = HomeBlue,
                trackColor = HomeDivider,
            )
        } else {
            LinearProgressIndicator(
                progress = { if (state.pendingCount == 0) 1f else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (state.pendingCount == 0) HomeGreen else HomeDivider,
                trackColor = HomeDivider,
            )
        }
        Text(
            text = syncStatusText(state),
            style = MaterialTheme.typography.bodySmall,
            color = HomeTextSecondary,
        )
        state.syncError?.let { error ->
            Text(
                text = error.syncErrorMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = HomeOrange,
            )
        }
    }
}

@Composable
private fun syncStatusText(state: OfflineUiState): String =
    when {
        state.isSyncing -> stringResource(R.string.offline_sync_in_progress)
        state.pendingCount == 0 -> stringResource(R.string.offline_sync_done)
        !state.internetAvailable -> stringResource(R.string.offline_sync_waiting)
        else -> stringResource(R.string.offline_sync_preparing)
    }

@Composable
private fun StorageSection(state: OfflineUiState) {
    val percentText = String.format(Locale.getDefault(), "%.1f", state.storagePercent * 100f)
    val lowSpace = state.storagePercent >= 0.9f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.offline_storage_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.offline_storage_value,
                    formatBytes(state.storageUsedBytes),
                    formatBytes(state.storageBudgetBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = HomeTextPrimary,
            )
        }
        LinearProgressIndicator(
            progress = { state.storagePercent },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = if (lowSpace) HomeOrange else HomeBlue,
            trackColor = HomeDivider,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(if (lowSpace) HomeOrange else HomeGreen, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.offline_storage_percent, percentText),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
            )
        }
    }
}

@Composable
private fun LastSyncCard(millis: Long?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.offline_last_sync),
                style = MaterialTheme.typography.bodyMedium,
                color = HomeTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = lastSyncText(millis),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = HomeTextSecondary,
            )
        }
    }
}

@Composable
private fun RecordsRow(
    state: OfflineUiState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = HomeTextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offline_view_records),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                Text(
                    text = recordsSubtitle(state.totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = HomeTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun recordsSubtitle(count: Int): String =
    if (count == 0) {
        stringResource(R.string.offline_view_records_subtitle)
    } else {
        pluralStringResource(R.plurals.offline_stored_count, count, count)
    }
