package com.app.drivedata.ui.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.vehicle.model.Vehicle

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    onOpenDetails: () -> Unit,
    onSelectActive: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDetails),
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
                    text = lastCommunicationText(vehicle.connection.lastEcuCommunicationAtMillis),
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
                TextButton(onClick = onOpenDetails) {
                    Text(stringResource(R.string.vehicle_details_open))
                }
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
