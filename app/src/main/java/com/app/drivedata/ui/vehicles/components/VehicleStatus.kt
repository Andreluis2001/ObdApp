package com.app.drivedata.ui.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.ui.common.formatClockTime
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.common.isSameDay
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeOrange
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.vehicle.model.ConnectionStatus

@Composable
internal fun ActiveBadge() {
    Row(
        modifier =
            Modifier
                .background(HomeBlue.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = HomeBlue,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.vehicle_active_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = HomeBlue,
        )
    }
}

@Composable
internal fun ConnectionStatus.label(): String =
    stringResource(
        when (this) {
            ConnectionStatus.NOT_CONFIGURED -> R.string.vehicle_status_not_configured
            ConnectionStatus.CONNECTED -> R.string.vehicle_status_connected
            ConnectionStatus.DISCONNECTED -> R.string.vehicle_status_disconnected
            ConnectionStatus.ERROR -> R.string.vehicle_status_error
        },
    )

internal fun ConnectionStatus.color(): Color =
    when (this) {
        ConnectionStatus.NOT_CONFIGURED -> HomeTextSecondary
        ConnectionStatus.CONNECTED -> HomeGreen
        ConnectionStatus.DISCONNECTED -> HomeOrange
        ConnectionStatus.ERROR -> HomeRed
    }

/** The connected state is redundant on cards and details; warning states remain visible. */
internal fun ConnectionStatus.shouldShowStatus(): Boolean = this != ConnectionStatus.CONNECTED

@Composable
internal fun lastCommunicationText(millis: Long?): String =
    when {
        millis == null -> stringResource(R.string.vehicle_never)
        isSameDay(millis) -> stringResource(R.string.vehicle_today, formatClockTime(millis))
        else -> formatDateTime(millis)
    }
