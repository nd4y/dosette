package icu.nd4y.dosette.ui.appointments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.common.currentLocale
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.ScreenHeader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun AppointmentEditScreen(
    appointmentId: String?,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppointmentEditViewModel =
        hiltViewModel<AppointmentEditViewModel, AppointmentEditViewModel.Factory>(
            creationCallback = { factory -> factory.create(appointmentId) },
        ),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    AppointmentEditContent(
        draft = draft,
        contentPadding = contentPadding,
        onUpdate = viewModel::update,
        onSave = { viewModel.save(onDone) },
        onDelete = { viewModel.delete(onDone) },
        onBack = onDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentEditContent(
    draft: AppointmentDraft,
    contentPadding: PaddingValues,
    onUpdate: ((AppointmentDraft) -> AppointmentDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    // An existing appointment loads asynchronously; rendering the empty
    // draft first would let typed text be overwritten by the loaded one.
    if (!draft.loaded) return

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title =
                stringResource(
                    if (draft.editingExisting) {
                        R.string.appointment_edit_title
                    } else {
                        R.string.appointment_new_title
                    },
                ),
            onBack = onBack,
        )

        OutlinedTextField(
            value = draft.title,
            onValueChange = { value -> onUpdate { it.copy(title = value) } },
            label = { Text(stringResource(R.string.appointment_field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.doctor,
            onValueChange = { value -> onUpdate { it.copy(doctor = value) } },
            label = { Text(stringResource(R.string.appointment_field_doctor)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.location,
            onValueChange = { value -> onUpdate { it.copy(location = value) } },
            label = { Text(stringResource(R.string.appointment_field_location)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                DateTimeRow(
                    title = stringResource(R.string.appointment_field_date),
                    value = formatDate(draft.date),
                    onClick = { datePickerOpen = true },
                )
                DateTimeRow(
                    title = stringResource(R.string.appointment_field_time),
                    value = formatTime(draft.time),
                    onClick = { timePickerOpen = true },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.appointment_remind),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                OFFSET_CHOICES.forEach { (minutes, labelRes) ->
                    FilterChip(
                        selected = minutes in draft.offsets,
                        onClick = {
                            onUpdate {
                                it.copy(
                                    offsets =
                                        if (minutes in it.offsets) {
                                            it.offsets - minutes
                                        } else {
                                            it.offsets + minutes
                                        },
                                )
                            }
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.notes,
            onValueChange = { value -> onUpdate { it.copy(notes = value) } },
            label = { Text(stringResource(R.string.appointment_field_notes)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onSave,
            enabled = draft.valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }

        if (draft.editingExisting) {
            TextButton(
                onClick = { deleteConfirmOpen = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(R.string.appointment_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (datePickerOpen) {
        AppointmentDatePicker(
            initial = draft.date,
            onDismiss = { datePickerOpen = false },
            onPick = { date ->
                datePickerOpen = false
                onUpdate { it.copy(date = date) }
            },
        )
    }

    if (timePickerOpen) {
        AppointmentTimePicker(
            initial = draft.time,
            onDismiss = { timePickerOpen = false },
            onPick = { time ->
                timePickerOpen = false
                onUpdate { it.copy(time = time) }
            },
        )
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text(stringResource(R.string.appointment_delete)) },
            text = { Text(stringResource(R.string.appointment_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmOpen = false
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private val OFFSET_CHOICES =
    listOf(
        30 to R.string.appointment_offset_30m,
        120 to R.string.appointment_offset_2h,
        1440 to R.string.appointment_offset_1d,
    )

@Composable
private fun DateTimeRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = DosetteIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis =
                initial
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                onPick(
                    Instant
                        .ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate(),
                )
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentTimePicker(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = state)
            }
        },
    )
}

@Composable
private fun formatDate(date: LocalDate): String {
    val locale = currentLocale()
    val format = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    return format.format(date)
}

@Composable
private fun formatTime(time: LocalTime): String {
    val locale = currentLocale()
    val format = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    return format.format(time)
}
