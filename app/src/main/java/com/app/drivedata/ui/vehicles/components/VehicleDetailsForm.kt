package com.app.drivedata.ui.vehicles.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.vehicle.VehicleDetailsDraft
import com.app.drivedata.vehicle.VehicleDetailsErrors
import com.app.drivedata.vehicle.VehicleDetailsValidator

/** The shared vehicle information fields used by the add and edit flows. */
@Composable
fun VehicleDetailsForm(
    details: VehicleDetailsDraft,
    errors: VehicleDetailsErrors,
    onMakeChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onPlateChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = details.make,
            onValueChange = onMakeChange,
            singleLine = true,
            label = { Text(stringResource(R.string.vehicle_make_label)) },
            isError = errors.make,
            supportingText =
                if (errors.make) {
                    { Text(stringResource(R.string.vehicle_error_make)) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = details.model,
            onValueChange = onModelChange,
            singleLine = true,
            label = { Text(stringResource(R.string.vehicle_model_label)) },
            isError = errors.model,
            supportingText =
                if (errors.model) {
                    { Text(stringResource(R.string.vehicle_error_model)) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = details.year,
            onValueChange = onYearChange,
            singleLine = true,
            label = { Text(stringResource(R.string.vehicle_year_label)) },
            isError = errors.year,
            supportingText =
                if (errors.year) {
                    { Text(stringResource(R.string.vehicle_error_year)) }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = details.licensePlate,
            onValueChange = onPlateChange,
            singleLine = true,
            label = { Text(stringResource(R.string.vehicle_plate_label)) },
            isError = errors.licensePlate,
            supportingText =
                if (errors.licensePlate) {
                    { Text(stringResource(R.string.vehicle_error_plate)) }
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = details.nickname,
            onValueChange = onNicknameChange,
            singleLine = true,
            label = { Text(stringResource(R.string.vehicle_nickname_label)) },
            isError = errors.nickname,
            supportingText =
                if (errors.nickname) {
                    {
                        Text(
                            stringResource(
                                R.string.vehicle_error_nickname,
                                VehicleDetailsValidator.MAX_NICKNAME_LENGTH,
                            ),
                        )
                    }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
