package com.app.drivedata.ui.vehicles.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.ui.common.AppCard
import com.app.drivedata.ui.common.StatusLabel
import com.app.drivedata.ui.theme.Dimens
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.vehicle.model.Vehicle

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = vehicle.isActive
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        border =
            BorderStroke(
                width = if (active) 2.dp else 1.dp,
                color = if (active) HomeBlue else HomeDivider,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(HomeBlue.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = HomeBlue,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.width(Dimens.CardSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = vehicle.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = HomeTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                vehicleSubtitle(vehicle)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = HomeTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (vehicle.connection.status.shouldShowStatus()) {
                    StatusLabel(
                        color = vehicle.connection.status.color(),
                        label = vehicle.connection.status.label(),
                    )
                }
            }
            if (active) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.vehicle_active_badge),
                    tint = HomeBlue,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun vehicleSubtitle(vehicle: Vehicle): String? =
    vehicle.details.licensePlate ?: vehicle.identity?.vin
