package com.app.drivedata.ui.vehicles.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.drivedata.metrics.model.MetricId
import com.app.drivedata.ui.common.TintedIconBadge
import com.app.drivedata.ui.metrics.presentation
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary

/** A discovered parameter row: tinted icon, label, subtitle and a collection toggle. */
@Composable
fun DiscoveredPidRow(
    metricId: MetricId,
    pid: Int,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = metricId.presentation()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedIconBadge(
            icon = presentation.icon,
            tint = presentation.tint,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(presentation.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = HomeTextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { checked -> onToggle(pid, checked) },
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HomeBlue,
                    checkedBorderColor = HomeBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = HomeDivider,
                    uncheckedBorderColor = HomeTextSecondary,
                ),
        )
    }
}
