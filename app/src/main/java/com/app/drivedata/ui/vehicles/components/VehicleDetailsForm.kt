package com.app.drivedata.ui.vehicles.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.ui.common.SectionHeader
import com.app.drivedata.vehicle.VehicleDetailsDraft
import com.app.drivedata.vehicle.VehicleDetailsErrors

/** The shared vehicle information fields used by the add and edit flows. */
@Composable
fun VehicleDetailsForm(
    details: VehicleDetailsDraft,
    errors: VehicleDetailsErrors,
    onMakeChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onPlateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.form_section_identification))
        FormTextField(
            value = details.make,
            onValueChange = onMakeChange,
            label = stringResource(R.string.vehicle_make_label),
            icon = Icons.Filled.DirectionsCar,
            isError = errors.make,
            errorText = stringResource(R.string.vehicle_error_make),
        )
        FormTextField(
            value = details.model,
            onValueChange = onModelChange,
            label = stringResource(R.string.vehicle_model_label),
            icon = Icons.Filled.Category,
            isError = errors.model,
            errorText = stringResource(R.string.vehicle_error_model),
        )
        FormTextField(
            value = details.licensePlate,
            onValueChange = onPlateChange,
            label = stringResource(R.string.vehicle_plate_label),
            icon = Icons.Filled.Tag,
            isError = errors.licensePlate,
            errorText =
                if (details.licensePlate.isBlank()) {
                    stringResource(R.string.vehicle_error_plate_required)
                } else {
                    stringResource(R.string.vehicle_error_plate_invalid)
                },
            capitalization = KeyboardCapitalization.Characters,
        )
    }
}
