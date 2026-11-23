package com.app.drivedata.ui.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import com.app.drivedata.BuildConfig
import com.app.drivedata.R
import com.app.drivedata.metrics.CollectionInterval
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.ScreenHeader
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary

private enum class ProfileDialog { COLLECTION, ABOUT }

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenVehicles: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LightStatusBarIcons()
    ProfileContent(
        state = state,
        onOpenVehicles = onOpenVehicles,
        onCollectionIntervalSelected = viewModel::setCollectionIntervalSeconds,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onOpenVehicles: () -> Unit,
    onCollectionIntervalSelected: (Long) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeDialog by rememberSaveable { mutableStateOf<ProfileDialog?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        ScreenHeader(title = stringResource(R.string.profile_title))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IdentityCard(state = state)

            ProfileMenuRow(
                icon = Icons.Filled.DirectionsCar,
                title = stringResource(R.string.vehicles_title),
                subtitle = pluralStringResource(
                    R.plurals.profile_vehicle_count,
                    state.vehicleCount,
                    state.vehicleCount,
                ),
                onClick = onOpenVehicles,
            )
            ProfileMenuRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.profile_collection_settings),
                subtitle = stringResource(
                    R.string.profile_collection_interval,
                    state.collectionIntervalSeconds,
                ),
                onClick = { activeDialog = ProfileDialog.COLLECTION },
            )
            ProfileMenuRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.profile_about),
                subtitle = stringResource(R.string.profile_version, BuildConfig.VERSION_NAME),
                onClick = { activeDialog = ProfileDialog.ABOUT },
            )

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HomeRed.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeRed),
            ) {
                Text(
                    text = stringResource(R.string.action_sign_out),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    when (activeDialog) {
        ProfileDialog.COLLECTION ->
            CollectionDialog(
                intervalSeconds = state.collectionIntervalSeconds,
                onSave = onCollectionIntervalSelected,
                onDismiss = { activeDialog = null },
            )

        ProfileDialog.ABOUT -> AboutDialog(onDismiss = { activeDialog = null })
        null -> Unit
    }
}

@Composable
private fun IdentityCard(state: ProfileUiState) {
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
                    .size(60.dp)
                    .background(HomeTextSecondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = state.displayName.ifBlank { state.username },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                state.email?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HomeTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                imageVector = icon,
                contentDescription = null,
                tint = HomeTextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                Text(
                    text = subtitle,
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
private fun CollectionDialog(
    intervalSeconds: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable(intervalSeconds) { mutableStateOf(intervalSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomeSurface,
        titleContentColor = HomeTextPrimary,
        textContentColor = HomeTextSecondary,
        title = { Text(stringResource(R.string.profile_collection_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.profile_collection_dialog_message,
                            selected.toInt(),
                            selected,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.profile_collection_interval_value, selected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
                Slider(
                    value = selected.toFloat(),
                    onValueChange = { selected = it.roundToInt().toLong() },
                    valueRange =
                        CollectionInterval.MIN_SECONDS.toFloat()..
                            CollectionInterval.MAX_SECONDS.toFloat(),
                    steps =
                        (CollectionInterval.MAX_SECONDS - CollectionInterval.MIN_SECONDS)
                            .toInt() - 1,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = HomeBlue,
                            activeTrackColor = HomeBlue,
                        ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.profile_collection_interval_min,
                                CollectionInterval.MIN_SECONDS,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = HomeTextSecondary,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.profile_collection_interval_max,
                                CollectionInterval.MAX_SECONDS,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = HomeTextSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(selected)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomeSurface,
        titleContentColor = HomeTextPrimary,
        textContentColor = HomeTextSecondary,
        title = { Text(stringResource(R.string.profile_about_dialog_title)) },
        text = {
            Text(
                text = stringResource(R.string.profile_about_dialog_message, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
