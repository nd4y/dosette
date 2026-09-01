package icu.nd4y.dosette.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.common.formatAmount
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import java.time.LocalTime

/** Medication + time + amount for a one-off dose of the shown day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOneOffDialog(
    medications: List<OneOffMedOption>,
    onConfirm: (String, LocalTime, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var med by remember { mutableStateOf(medications.first()) }
    var medMenuOpen by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(LocalTime.of(DEFAULT_ONE_OFF_HOUR, 0)) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf(formatAmount(med.defaultAmount)) }
    val amount = amountText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oneoff_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    PickerRow(
                        label = stringResource(R.string.oneoff_medication),
                        onClick = { medMenuOpen = true },
                    ) {
                        MedIconBox(form = med.form, colorSeed = med.colorSeed, size = 26.dp)
                        Text(
                            text = med.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(expanded = medMenuOpen, onDismissRequest = { medMenuOpen = false }) {
                        medications.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                leadingIcon = {
                                    MedIconBox(form = option.form, colorSeed = option.colorSeed, size = 26.dp)
                                },
                                onClick = {
                                    med = option
                                    amountText = formatAmount(option.defaultAmount)
                                    medMenuOpen = false
                                },
                            )
                        }
                    }
                }
                PickerRow(
                    label = stringResource(R.string.oneoff_time),
                    onClick = { timePickerOpen = true },
                ) {
                    Text(
                        text = time.format(TimeFormat),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.times_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amount == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0,
                onClick = { onConfirm(med.id, time, requireNotNull(amount)) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (timePickerOpen) {
        val pickerState =
            rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(pickerState.hour, pickerState.minute)
                    timePickerOpen = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { timePickerOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

/** Labelled tappable field of the one-off dialog. */
@Composable
private fun PickerRow(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                content()
            }
        }
    }
}

private const val DEFAULT_ONE_OFF_HOUR = 12
