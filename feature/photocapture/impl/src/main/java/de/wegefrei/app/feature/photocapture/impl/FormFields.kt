package de.wegefrei.app.feature.photocapture.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.wegefrei.app.core.designsystem.rememberTouchedFieldState

internal fun filterOptions(
    query: String,
    options: List<String>,
): List<String> = options.filter { it.contains(query, ignoreCase = true) }

// Matches the query against either the committed value or its (possibly translated) display
// text, so users can find an option by typing in either language — returns indices so the
// caller can commit `options[index]` while rendering `displayOptions[index]`.
internal fun filterOptionIndices(
    query: String,
    options: List<String>,
    displayOptions: List<String>,
): List<Int> =
    options.indices.filter { index ->
        options[index].contains(query, ignoreCase = true) || displayOptions[index].contains(query, ignoreCase = true)
    }

@Composable
internal fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val touchedField = rememberTouchedFieldState()
    val isError = touchedField.touched && value.isBlank()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(R.string.label_required_suffix, label)) },
        isError = isError,
        supportingText =
            if (isError) {
                { Text(text = stringResource(R.string.error_required_field)) }
            } else {
                null
            },
        modifier =
            modifier
                .fillMaxWidth()
                .then(touchedField.modifier),
    )
}

@Composable
internal fun RequiredSwitchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onLabel: String,
    offLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = value == onLabel,
            onCheckedChange = { checked -> onValueChange(if (checked) onLabel else offLabel) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RequiredOptionDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    displayOptions: List<String> = options,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val touchedField = rememberTouchedFieldState(onFocusChanged = { isFocused -> expanded = isFocused })
    val isError = touchedField.touched && value.isBlank()
    val filteredIndices =
        remember(value, options, displayOptions) {
            filterOptionIndices(value, options, displayOptions)
        }
    // The committed value always stays whatever was picked/typed (e.g. the canonical German
    // text for a known option), but once it exactly matches a known option, show that option's
    // display text instead — so selecting a suggestion doesn't visually revert to German.
    val displayValue =
        remember(value, options, displayOptions) {
            val index = options.indexOf(value)
            if (index >= 0) displayOptions[index] else value
        }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredIndices.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(text = stringResource(R.string.label_required_suffix, label)) },
            isError = isError,
            supportingText =
                if (isError) {
                    { Text(text = stringResource(R.string.error_required_field)) }
                } else {
                    null
                },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = Modifier.menuAnchor(MenuAnchorType.SecondaryEditable),
                )
            },
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
                    .then(touchedField.modifier),
        )

        ExposedDropdownMenu(
            expanded = expanded && filteredIndices.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filteredIndices.forEach { index ->
                DropdownMenuItem(
                    text = { Text(text = displayOptions[index]) },
                    onClick = {
                        onValueChange(options[index])
                        expanded = false
                    },
                )
            }
        }
    }
}
