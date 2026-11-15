package com.app.drivedata.ui.vehicles.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary

private val FieldShape = RoundedCornerShape(14.dp)

/**
 * Outlined field styled for the brand palette: rounded border, leading icon and a
 * reserved supporting-text slot so validation errors do not shift the layout.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector?,
    isError: Boolean,
    errorText: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Unspecified,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        leadingIcon =
            if (icon != null) {
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else HomeTextSecondary,
                    )
                }
            } else {
                null
            },
        isError = isError,
        supportingText = {
            Text(
                text = if (isError) errorText else "",
                color = MaterialTheme.colorScheme.error,
            )
        },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
            ),
        shape = FieldShape,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HomeBlue,
                unfocusedBorderColor = HomeDivider,
                focusedLabelColor = HomeBlue,
                unfocusedLabelColor = HomeTextSecondary,
                cursorColor = HomeBlue,
                errorBorderColor = MaterialTheme.colorScheme.error,
            ),
        textStyle =
            MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                color = HomeTextPrimary,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}
