package com.app.drivedata.ui.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.ui.common.formatClockTime
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.common.isSameDay
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeGreen
import com.app.drivedata.ui.theme.HomeOrange
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.vehicle.model.ConnectionStatus
import com.app.drivedata.vehicle.model.Vehicle

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    onSelectActive: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(HomeBlue.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = HomeBlue,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = vehicle.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeTextPrimary,
                        maxLines = 2,
                    )
                    vehicleSubtitle(vehicle)?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTextSecondary,
                        )
                    }
                }
                if (vehicle.isActive) {
                    ActiveBadge()
                }
            }

            HorizontalDivider(color = HomeDivider)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(9.dp)
                            .background(vehicle.connection.status.color(), CircleShape),
                )
                Text(
                    text = vehicle.connection.status.label(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = vehicle.connection.status.color(),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.vehicle_last_ecu),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = lastEcuText(vehicle.connection.lastEcuCommunicationAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextPrimary,
                    textAlign = TextAlign.End,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!vehicle.isActive) {
                    TextButton(onClick = onSelectActive) {
                        Text(stringResource(R.string.vehicle_select_active))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.vehicle_remove),
                        color = HomeRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveBadge() {
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
private fun vehicleSubtitle(vehicle: Vehicle): String? {
    val year = vehicle.details.year
    val plate = vehicle.details.licensePlate
    return when {
        year != null && plate != null ->
            stringResource(R.string.vehicle_year_plate_value, year, plate)

        year != null -> stringResource(R.string.vehicle_year_value, year)
        plate != null -> plate
        else -> null
    }
}

@Composable
private fun lastEcuText(millis: Long?): String =
    when {
        millis == null -> stringResource(R.string.vehicle_never)
        isSameDay(millis) -> stringResource(R.string.vehicle_today, formatClockTime(millis))
        else -> formatDateTime(millis)
    }

@Composable
private fun ConnectionStatus.label(): String =
    stringResource(
        when (this) {
            ConnectionStatus.NOT_CONFIGURED -> R.string.vehicle_status_not_configured
            ConnectionStatus.CONNECTED -> R.string.vehicle_status_connected
            ConnectionStatus.DISCONNECTED -> R.string.vehicle_status_disconnected
            ConnectionStatus.ERROR -> R.string.vehicle_status_error
        },
    )

private fun ConnectionStatus.color(): Color =
    when (this) {
        ConnectionStatus.NOT_CONFIGURED -> HomeTextSecondary
        ConnectionStatus.CONNECTED -> HomeGreen
        ConnectionStatus.DISCONNECTED -> HomeOrange
        ConnectionStatus.ERROR -> HomeRed
    }
