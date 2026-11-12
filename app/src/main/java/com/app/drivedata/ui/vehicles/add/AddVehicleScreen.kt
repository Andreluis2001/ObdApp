package com.app.drivedata.ui.vehicles.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.common.formatDateTime
import com.app.drivedata.ui.metrics.presentation
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.vehicles.AddVehicleEvent
import com.app.drivedata.ui.vehicles.DiscoveredPid
import com.app.drivedata.ui.vehicles.FormStep
import com.app.drivedata.ui.vehicles.SetupStage
import com.app.drivedata.ui.vehicles.VehicleSetupState
import com.app.drivedata.ui.vehicles.components.VehicleDetailsForm
import com.app.drivedata.ui.vehicles.components.VehicleHeader
import com.app.drivedata.ui.vehicles.labelRes
import com.app.drivedata.ui.vehicles.messageRes
import com.app.drivedata.vehicle.ConnectionConfigErrors
import com.app.drivedata.vehicle.ConnectionDraft
import com.app.drivedata.vehicle.VehicleDetailsDraft
import com.app.drivedata.vehicle.VehicleDetailsErrors

@Composable
fun AddVehicleScreen(
    viewModel: AddVehicleViewModel,
    onExit: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()

    BackHandler { viewModel.onBack() }
    LaunchedEffect(event) {
        when (val current = event) {
            is AddVehicleEvent.Saved -> {
                viewModel.consumeEvent()
                onSaved(current.vehicleId)
            }

            AddVehicleEvent.Cancelled -> {
                viewModel.consumeEvent()
                onExit()
            }

            null -> Unit
        }
    }

    LightStatusBarIcons()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeBackground),
    ) {
        VehicleHeader(
            title = stringResource(R.string.add_vehicle_title),
            subtitle = state.subtitleRes()?.let { stringResource(it) },
            onBack = viewModel::onBack,
        )
        SetupProgress(currentStep = state.progressStep())

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = state) {
                is VehicleSetupState.Form ->
                    if (current.step == FormStep.VEHICLE) {
                        VehicleFormStep(
                            details = current.details,
                            errors = current.errors.details,
                            onMakeChange = viewModel::onMakeChange,
                            onModelChange = viewModel::onModelChange,
                            onYearChange = viewModel::onYearChange,
                            onPlateChange = viewModel::onPlateChange,
                            onNicknameChange = viewModel::onNicknameChange,
                            onContinue = viewModel::onContinue,
                        )
                    } else {
                        AdapterFormStep(
                            connection = current.connection,
                            errors = current.errors.connection,
                            onHostChange = viewModel::onHostChange,
                            onPortChange = viewModel::onPortChange,
                            onTestConnection = viewModel::onTestConnection,
                            onSaveWithoutTesting = viewModel::onSaveWithoutTesting,
                            onBack = viewModel::onBackToVehicleStep,
                        )
                    }

                VehicleSetupState.ConnectingAdapter ->
                    BusyStep(messageRes = R.string.add_vehicle_connecting)

                VehicleSetupState.InitializingAdapter ->
                    BusyStep(messageRes = R.string.add_vehicle_initializing)

                VehicleSetupState.TestingEcu ->
                    BusyStep(messageRes = R.string.add_vehicle_testing_ecu)

                is VehicleSetupState.DiscoveringPids ->
                    BusyStep(
                        messageRes = R.string.add_vehicle_discovering,
                        detail =
                            stringResource(
                                R.string.add_vehicle_discovery_progress,
                                current.rangeBase,
                                current.foundCount,
                            ),
                    )

                is VehicleSetupState.Review ->
                    ReviewStep(
                        review = current,
                        onPidToggle = viewModel::onPidToggle,
                        onRediscover = viewModel::onRediscover,
                        onSave = viewModel::onSave,
                    )

                is VehicleSetupState.Failed ->
                    FailedStep(
                        failed = current,
                        onRetry = viewModel::onRetry,
                        onBack = viewModel::onBack,
                        onSaveAnyway = viewModel::onSave,
                    )

                VehicleSetupState.Saving ->
                    BusyStep(messageRes = R.string.add_vehicle_saving)
            }

            if (state !is VehicleSetupState.Saving) {
                TextButton(
                    onClick = viewModel::cancelFlow,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(R.string.add_vehicle_cancel),
                        color = HomeTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupProgress(currentStep: Int) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(HomeSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            R.string.add_vehicle_step_vehicle,
            R.string.add_vehicle_step_adapter,
            R.string.add_vehicle_step_parameters,
        ).forEachIndexed { index, labelRes ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                color = if (index <= currentStep) HomeBlue else HomeDivider,
                                shape = RoundedCornerShape(2.dp),
                            ),
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index <= currentStep) HomeBlue else HomeTextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun VehicleFormStep(
    details: VehicleDetailsDraft,
    errors: VehicleDetailsErrors,
    onMakeChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onPlateChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.add_vehicle_step_vehicle)) {
        VehicleDetailsForm(
            details = details,
            errors = errors,
            onMakeChange = onMakeChange,
            onModelChange = onModelChange,
            onYearChange = onYearChange,
            onPlateChange = onPlateChange,
            onNicknameChange = onNicknameChange,
        )
        PrimaryButton(
            label = stringResource(R.string.add_vehicle_continue),
            onClick = onContinue,
        )
    }
}

@Composable
private fun AdapterFormStep(
    connection: ConnectionDraft,
    errors: ConnectionConfigErrors,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSaveWithoutTesting: () -> Unit,
    onBack: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.add_vehicle_step_adapter)) {
        Text(
            text = stringResource(R.string.add_vehicle_adapter_hint),
            style = MaterialTheme.typography.bodySmall,
            color = HomeTextSecondary,
        )
        OutlinedTextField(
            value = connection.host,
            onValueChange = onHostChange,
            singleLine = true,
            label = { Text(stringResource(R.string.connection_host_label)) },
            isError = errors.host,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = connection.port,
            onValueChange = onPortChange,
            singleLine = true,
            label = { Text(stringResource(R.string.connection_port_label)) },
            isError = errors.port,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (errors.host || errors.port) {
            Text(
                text = stringResource(R.string.failure_invalid_configuration),
                style = MaterialTheme.typography.bodySmall,
                color = HomeRed,
            )
        }
        PrimaryButton(
            label = stringResource(R.string.add_vehicle_test_connection),
            onClick = onTestConnection,
        )
        OutlinedButton(
            onClick = onSaveWithoutTesting,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.add_vehicle_save_without_test))
        }
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.add_vehicle_back_to_form))
        }
    }
}

@Composable
private fun ReviewStep(
    review: VehicleSetupState.Review,
    onPidToggle: (Int, Boolean) -> Unit,
    onRediscover: () -> Unit,
    onSave: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.add_vehicle_review_title)) {
        Text(
            text = stringResource(R.string.add_vehicle_review_hint),
            style = MaterialTheme.typography.bodySmall,
            color = HomeTextSecondary,
        )
        Text(
            text =
                stringResource(
                    R.string.add_vehicle_discovered_at,
                    formatDateTime(review.discoveredAtMillis),
                ),
            style = MaterialTheme.typography.labelSmall,
            color = HomeTextSecondary,
        )
        if (review.pids.isEmpty()) {
            Text(
                text = stringResource(R.string.add_vehicle_no_pids),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextSecondary,
            )
        } else {
            review.pids.forEach { pid ->
                DiscoveredPidRow(pid = pid, onToggle = onPidToggle)
            }
        }
        OutlinedButton(
            onClick = onRediscover,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_vehicle_rediscover))
        }
        PrimaryButton(
            label = stringResource(R.string.add_vehicle_save),
            onClick = onSave,
        )
    }
}

@Composable
private fun DiscoveredPidRow(pid: DiscoveredPid, onToggle: (Int, Boolean) -> Unit) {
    val presentation = pid.metricId.presentation()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .background(presentation.tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = presentation.icon,
                contentDescription = null,
                tint = presentation.tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(presentation.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = HomeTextPrimary,
            )
            Text(
                text =
                    stringResource(presentation.unitRes) +
                        " · " +
                        stringResource(R.string.add_vehicle_pid_value, pid.pid),
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextSecondary,
            )
        }
        Switch(
            checked = pid.enabled,
            onCheckedChange = { enabled -> onToggle(pid.pid, enabled) },
        )
    }
}

@Composable
private fun FailedStep(
    failed: VehicleSetupState.Failed,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSaveAnyway: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = HomeRed,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.add_vehicle_failure_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
            )
        }
        Text(
            text = stringResource(failed.stage.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = HomeTextSecondary,
        )
        Text(
            text = stringResource(failed.failure.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = HomeTextPrimary,
        )
        PrimaryButton(
            label =
                stringResource(
                    if (failed.stage == SetupStage.SAVE) {
                        R.string.add_vehicle_retry_save
                    } else {
                        R.string.add_vehicle_retry
                    },
                ),
            onClick = onRetry,
        )
        OutlinedButton(
            onClick = onBack,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.add_vehicle_back_to_form))
        }
        if (failed.stage != SetupStage.SAVE) {
            TextButton(
                onClick = onSaveAnyway,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.add_vehicle_save_anyway))
            }
        }
    }
}

@Composable
private fun BusyStep(messageRes: Int, detail: String? = null) {
    SectionCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = HomeBlue)
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
                textAlign = TextAlign.Center,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                )
            }
            content()
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HomeBlue),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun VehicleSetupState.subtitleRes(): Int? =
    when (this) {
        is VehicleSetupState.Form ->
            if (step == FormStep.VEHICLE) {
                R.string.add_vehicle_step_vehicle
            } else {
                R.string.add_vehicle_step_adapter
            }

        VehicleSetupState.ConnectingAdapter,
        VehicleSetupState.InitializingAdapter,
        VehicleSetupState.TestingEcu,
        is VehicleSetupState.DiscoveringPids,
        -> R.string.add_vehicle_step_adapter

        is VehicleSetupState.Review -> R.string.add_vehicle_step_parameters
        is VehicleSetupState.Failed -> stage.labelRes()
        VehicleSetupState.Saving -> R.string.add_vehicle_step_parameters
    }

private fun VehicleSetupState.progressStep(): Int =
    when (this) {
        is VehicleSetupState.Form -> if (step == FormStep.VEHICLE) 0 else 1
        VehicleSetupState.ConnectingAdapter,
        VehicleSetupState.InitializingAdapter,
        VehicleSetupState.TestingEcu,
        is VehicleSetupState.DiscoveringPids,
        -> 1

        is VehicleSetupState.Review -> 2
        is VehicleSetupState.Failed ->
            if (stage == SetupStage.SAVE || stage == SetupStage.DISCOVERY) 2 else 1

        VehicleSetupState.Saving -> 2
    }
