package com.app.drivedata.ui.vehicles.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.drivedata.R
import com.app.drivedata.ui.common.LightStatusBarIcons
import com.app.drivedata.ui.theme.HomeBackground
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeRed
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary
import com.app.drivedata.ui.vehicles.components.VehicleDetailsForm
import com.app.drivedata.ui.vehicles.components.VehicleErrorBanner
import com.app.drivedata.ui.vehicles.components.VehicleHeader

@Composable
fun EditVehicleScreen(
    viewModel: EditVehicleViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onSaved()
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
            title = stringResource(R.string.edit_vehicle_title),
            subtitle = stringResource(R.string.edit_vehicle_subtitle),
            onBack = onBack,
        )

        when {
            state.isLoading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = HomeBlue)
                }

            state.loadFailed ->
                Text(
                    text = stringResource(R.string.edit_vehicle_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeTextSecondary,
                    modifier = Modifier.padding(20.dp),
                )

            else ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.saveFailed) {
                        VehicleErrorBanner(
                            message = stringResource(R.string.edit_vehicle_save_failed),
                            onDismiss = viewModel::dismissSaveError,
                        )
                    }

                    SectionCard(title = stringResource(R.string.edit_vehicle_section_vehicle)) {
                        VehicleDetailsForm(
                            details = state.draft,
                            errors = state.errors.details,
                            onMakeChange = viewModel::onMakeChange,
                            onModelChange = viewModel::onModelChange,
                            onYearChange = viewModel::onYearChange,
                            onPlateChange = viewModel::onPlateChange,
                            onNicknameChange = viewModel::onNicknameChange,
                        )
                    }

                    SectionCard(title = stringResource(R.string.edit_vehicle_section_adapter)) {
                        Text(
                            text = stringResource(R.string.add_vehicle_adapter_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTextSecondary,
                        )
                        OutlinedTextField(
                            value = state.connection.host,
                            onValueChange = viewModel::onHostChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.connection_host_label)) },
                            isError = state.errors.connection.host,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.connection.port,
                            onValueChange = viewModel::onPortChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.connection_port_label)) },
                            isError = state.errors.connection.port,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.errors.connection.host || state.errors.connection.port) {
                            Text(
                                text = stringResource(R.string.failure_invalid_configuration),
                                style = MaterialTheme.typography.bodySmall,
                                color = HomeRed,
                            )
                        }
                    }

                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HomeBlue),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                color = HomeSurface,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(stringResource(R.string.vehicle_save))
                        }
                    }
                }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextPrimary,
            )
            content()
        }
    }
}
