package com.app.drivedata.ui.offline

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.metrics.model.MetricReading
import com.app.drivedata.metrics.model.MetricRecord
import com.app.drivedata.metrics.model.ReadingValue
import com.app.drivedata.ui.common.ScreenHeader
import com.app.drivedata.ui.common.SectionHeader
import com.app.drivedata.ui.common.TintedIconBadge
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.common.formatMetricValue
import com.app.drivedata.ui.metrics.MetricPresentation
import com.app.drivedata.ui.metrics.parameterOrder
import com.app.drivedata.ui.metrics.presentation
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary

@Composable
fun MetricDetailsScreen(
    state: RecordDetailsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        ScreenHeader(
            title = stringResource(R.string.offline_details_title),
            subtitle = stringResource(R.string.offline_details_subtitle),
            onBack = onBack,
        )

        when (state) {
            RecordDetailsUiState.Idle,
            RecordDetailsUiState.Loading,
            -> LoadingDetails(modifier = Modifier.weight(1f))

            is RecordDetailsUiState.Loaded -> {
                val record = state.record
                if (record == null) {
                    NotFoundDetails(modifier = Modifier.weight(1f))
                } else {
                    DetailsContent(record = record, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LoadingDetails(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = HomeBlue)
    }
}

@Composable
private fun NotFoundDetails(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
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
                    text = stringResource(R.string.offline_details_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.offline_details_not_found_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DetailsContent(
    record: MetricRecord,
    modifier: Modifier = Modifier,
) {
    val readings = record.readings.associateBy(MetricReading::id)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecordSummaryCard(record = record)

        SectionHeader(stringResource(R.string.offline_details_parameters))

        parameterOrder.forEach { id ->
            MetricDetailCard(
                presentation = id.presentation(),
                reading = readings[id],
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RecordSummaryCard(record: MetricRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDateTime(record.collectedAtMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(status = record.uploadStatus)
            }
            Text(
                text = recordVinText(record.vin),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
            )
            Text(
                text = recordStatusDetail(
                    status = record.uploadStatus,
                    uploadedAtMillis = record.uploadedAtMillis,
                    lastError = record.lastError,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor(record.uploadStatus),
            )
            if (record.attemptCount > 0) {
                Text(
                    text = stringResource(R.string.offline_details_attempts, record.attemptCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun MetricDetailCard(
    presentation: MetricPresentation,
    reading: MetricReading?,
) {
    val available = reading?.value as? ReadingValue.Available
    val unit = stringResource(presentation.unitRes)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TintedIconBadge(
                    icon = presentation.icon,
                    tint = presentation.tint,
                    badgeSize = 42.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(presentation.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = HomeTextSecondary,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text =
                                available?.let { formatMetricValue(it.value) }
                                    ?: stringResource(R.string.metrics_unavailable),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeTextPrimary,
                        )
                        if (available != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = unit,
                                style = MaterialTheme.typography.labelMedium,
                                color = HomeTextSecondary,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                }
            }
            if (available != null) {
                LinearProgressIndicator(
                    progress = { rangeFraction(presentation.range, available.value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = presentation.tint,
                    trackColor = HomeDivider,
                )
            }
            Text(
                text = stringResource(
                    R.string.offline_details_range,
                    formatMetricValue(presentation.range.start),
                    formatMetricValue(presentation.range.endInclusive),
                    unit,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
            )
        }
    }
}

private fun rangeFraction(
    range: ClosedFloatingPointRange<Double>,
    value: Double,
): Float {
    val span = range.endInclusive - range.start
    return if (span <= 0.0) {
        0f
    } else {
        ((value - range.start) / span).coerceIn(0.0, 1.0).toFloat()
    }
}
