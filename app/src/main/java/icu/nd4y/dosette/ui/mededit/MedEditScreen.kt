package icu.nd4y.dosette.ui.mededit

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.common.currentLocale
import icu.nd4y.dosette.ui.common.everyNDaysText
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import icu.nd4y.dosette.ui.designsystem.MedPalette
import icu.nd4y.dosette.ui.designsystem.rememberDirectionalMotion
import icu.nd4y.dosette.ui.designsystem.strokeGlyph
import icu.nd4y.dosette.ui.theme.LocalDarkTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

@Composable
fun MedEditScreen(
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    onBackOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.saved) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
        return
    }

    MedEditContent(
        state = state,
        contentPadding = contentPadding,
        onUpdate = viewModel::update,
        onNext = viewModel::next,
        onBack = { if (!viewModel.back()) onBackOut() },
        modifier = modifier,
    )
}

/** Stateless body — rendered directly by screenshot tests. */
@Composable
fun MedEditContent(
    state: MedEditUiState,
    contentPadding: PaddingValues,
    onUpdate: ((MedEditUiState) -> MedEditUiState) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
    ) {
        WizardHeader(state = state, onBack = onBack)

        val stepMotion = rememberDirectionalMotion()
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { stepMotion.transform(forward = targetState.ordinal > initialState.ordinal) },
            label = "wizard-step",
            // The weight must sit on AnimatedContent itself: inside the
            // content lambda the parent is AnimatedContent's own layout,
            // which ignores Column parent data — the footer would then be
            // measured with whatever height is left, down to zero.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
        ) { step ->
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(step.headlineRes()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                when (step) {
                    WizardStep.BASICS -> BasicsStep(state, onUpdate)
                    WizardStep.SCHEDULE -> ScheduleStep(state, onUpdate)
                    WizardStep.TIMES -> TimesStep(state, onUpdate)
                    WizardStep.STOCK -> StockStep(state, onUpdate)
                    WizardStep.REVIEW -> ReviewStep(state)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 16.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
            Button(
                onClick = onNext,
                enabled = state.canProceed,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
            ) {
                Text(
                    text =
                        if (state.stepIndex == state.stepCount - 1) {
                            stringResource(R.string.action_save)
                        } else {
                            stringResource(R.string.action_next)
                        },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun WizardStep.headlineRes(): Int =
    when (this) {
        WizardStep.BASICS -> R.string.wizard_headline_basics
        WizardStep.SCHEDULE -> R.string.wizard_headline_schedule
        WizardStep.TIMES -> R.string.wizard_headline_times
        WizardStep.STOCK -> R.string.wizard_headline_stock
        WizardStep.REVIEW -> R.string.wizard_headline_review
    }

private fun WizardStep.titleRes(): Int =
    when (this) {
        WizardStep.BASICS -> R.string.wizard_step_basics
        WizardStep.SCHEDULE -> R.string.wizard_step_schedule
        WizardStep.TIMES -> R.string.wizard_step_times
        WizardStep.STOCK -> R.string.wizard_step_stock
        WizardStep.REVIEW -> R.string.wizard_step_review
    }

@Composable
private fun WizardHeader(
    state: MedEditUiState,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(DosetteIcons.Back, contentDescription = stringResource(R.string.action_back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wizard_new_med),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.name.ifBlank { "…" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            MedIconBox(form = state.form, colorSeed = state.colorSeed, size = 36.dp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(state.stepCount) { index ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                if (index <= state.stepIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                RoundedCornerShape(3.dp),
                            ),
                )
            }
        }
        Text(
            text =
                stringResource(
                    R.string.wizard_step_of,
                    state.stepIndex + 1,
                    state.stepCount,
                    stringResource(state.step.titleRes()),
                ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicsStep(
    state: MedEditUiState,
    update: ((MedEditUiState) -> MedEditUiState) -> Unit,
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { value -> update { it.copy(name = value) } },
        label = { Text(stringResource(R.string.field_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MedicationForm.entries.forEach { form ->
            FilterChip(
                selected = state.form == form,
                onClick = { update { it.copy(form = form) } },
                label = { Text(stringResource(form.labelRes())) },
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.strengthText,
            onValueChange = { value -> update { it.copy(strengthText = value) } },
            label = { Text(stringResource(R.string.field_strength)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(2f),
        )
        OutlinedTextField(
            value = state.strengthUnit,
            onValueChange = { value -> update { it.copy(strengthUnit = value) } },
            label = { Text(stringResource(R.string.field_strength_unit)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }

    OutlinedTextField(
        value = state.instructions,
        onValueChange = { value -> update { it.copy(instructions = value) } },
        label = { Text(stringResource(R.string.field_instructions)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(R.string.field_color),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val dark = LocalDarkTheme.current
        repeat(MedPalette.size) { seed ->
            val color = MedPalette.resolve(seed, dark)
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(color.container, CircleShape)
                        .then(
                            if (state.colorSeed == seed) {
                                Modifier.background(Color.Transparent, CircleShape)
                            } else {
                                Modifier
                            },
                        ).clickable { update { it.copy(colorSeed = seed) } },
                contentAlignment = Alignment.Center,
            ) {
                if (state.colorSeed == seed) {
                    Icon(
                        imageVector = DosetteIcons.Check,
                        contentDescription = null,
                        tint = color.onContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun MedicationForm.labelRes(): Int =
    when (this) {
        MedicationForm.TABLET -> R.string.form_tablet
        MedicationForm.CAPSULE -> R.string.form_capsule
        MedicationForm.INJECTION -> R.string.form_injection
        MedicationForm.DROPS -> R.string.form_drops
        MedicationForm.LIQUID -> R.string.form_liquid
        MedicationForm.INHALER -> R.string.form_inhaler
        MedicationForm.OINTMENT -> R.string.form_ointment
        MedicationForm.SPRAY -> R.string.form_spray
        MedicationForm.OTHER -> R.string.form_other
    }

@Composable
private fun ScheduleStep(
    state: MedEditUiState,
    update: ((MedEditUiState) -> MedEditUiState) -> Unit,
) {
    ScheduleTypeCard(
        selected = state.scheduleType == ScheduleType.FIXED_TIMES,
        title = stringResource(R.string.schedule_type_daily),
        hint = stringResource(R.string.schedule_type_daily_hint),
        onClick = { update { it.copy(scheduleType = ScheduleType.FIXED_TIMES) } },
    )

    ScheduleTypeCard(
        selected = state.scheduleType == ScheduleType.WEEKDAYS,
        title = stringResource(R.string.schedule_type_weekdays),
        hint = stringResource(R.string.schedule_type_weekdays_hint),
        onClick = { update { it.copy(scheduleType = ScheduleType.WEEKDAYS) } },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DayOfWeek.entries.forEach { day ->
                val selectedDay = day in state.weekdays
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(
                                if (selectedDay) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(if (selectedDay) 13.dp else 20.dp),
                            ).then(
                                if (selectedDay) {
                                    Modifier
                                } else {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.surfaceContainerLowest,
                                        RoundedCornerShape(20.dp),
                                    )
                                },
                            ).clickable {
                                update {
                                    it.copy(
                                        weekdays =
                                            if (selectedDay) it.weekdays - day else it.weekdays + day,
                                    )
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, currentLocale()),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (selectedDay) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }

    ScheduleTypeCard(
        selected = state.scheduleType == ScheduleType.EVERY_N_DAYS,
        title = stringResource(R.string.schedule_type_every_n),
        hint = stringResource(R.string.schedule_type_every_n_hint),
        onClick = { update { it.copy(scheduleType = ScheduleType.EVERY_N_DAYS) } },
    ) {
        OutlinedTextField(
            value = state.intervalText,
            onValueChange = { value -> update { it.copy(intervalText = value) } },
            label = { Text(stringResource(R.string.interval_days_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    ScheduleTypeCard(
        selected = state.scheduleType == ScheduleType.CYCLE,
        title = stringResource(R.string.schedule_type_cycle),
        hint = stringResource(R.string.schedule_type_cycle_hint),
        onClick = { update { it.copy(scheduleType = ScheduleType.CYCLE) } },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.cycleOnText,
                onValueChange = { value -> update { it.copy(cycleOnText = value) } },
                label = { Text(stringResource(R.string.cycle_on_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.cycleOffText,
                onValueChange = { value -> update { it.copy(cycleOffText = value) } },
                label = { Text(stringResource(R.string.cycle_off_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }

    ScheduleTypeCard(
        selected = state.scheduleType == ScheduleType.AS_NEEDED,
        title = stringResource(R.string.schedule_type_prn),
        hint = stringResource(R.string.schedule_type_prn_hint),
        onClick = { update { it.copy(scheduleType = ScheduleType.AS_NEEDED) } },
    )
}

@Composable
private fun ScheduleTypeCard(
    selected: Boolean,
    title: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandedContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        border =
            androidx.compose.foundation.BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RadioButton(selected = selected, onClick = onClick)
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected && expandedContent != null) {
                expandedContent()
            }
        }
    }
}

@Composable
private fun TimesStep(
    state: MedEditUiState,
    update: ((MedEditUiState) -> MedEditUiState) -> Unit,
) {
    var pickerFor by remember { mutableStateOf<Int?>(null) }

    state.times.forEachIndexed { index, slot ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = { pickerFor = index },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = slot.time.format(TimeFormat),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            OutlinedTextField(
                value = slot.amountText,
                onValueChange = { value ->
                    update { s ->
                        s.copy(
                            times =
                                s.times.mapIndexed { i, t ->
                                    if (i == index) t.copy(amountText = value) else t
                                },
                        )
                    }
                },
                label = { Text(stringResource(R.string.times_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            if (state.times.size > 1) {
                IconButton(onClick = {
                    update { s -> s.copy(times = s.times.filterIndexed { i, _ -> i != index }) }
                }) {
                    Icon(
                        imageVector = RemoveIcon,
                        contentDescription = stringResource(R.string.times_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    TextButton(onClick = {
        update { s ->
            val nextTime = s.times.maxOfOrNull { it.time }?.plusHours(12) ?: LocalTime.of(8, 0)
            s.copy(times = s.times + TimeSlotDraft(time = nextTime))
        }
    }) {
        Text(stringResource(R.string.times_add))
    }

    pickerFor?.let { index ->
        val initial = state.times.getOrNull(index)?.time ?: LocalTime.of(8, 0)
        val pickerState =
            rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    update { s ->
                        s.copy(
                            times =
                                s.times.mapIndexed { i, t ->
                                    if (i == index) {
                                        t.copy(time = LocalTime.of(pickerState.hour, pickerState.minute))
                                    } else {
                                        t
                                    }
                                },
                        )
                    }
                    pickerFor = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerFor = null }) { Text(stringResource(android.R.string.cancel)) }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@Composable
private fun StockStep(
    state: MedEditUiState,
    update: ((MedEditUiState) -> MedEditUiState) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.stock_track_switch),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.stock_track_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.trackStock,
            onCheckedChange = { checked -> update { it.copy(trackStock = checked) } },
        )
    }

    if (state.trackStock) {
        state.variants.forEachIndexed { index, draft ->
            VariantCard(
                index = index,
                draft = draft,
                removable = state.variants.size > 1,
                unit = state.strengthUnit,
                onChange = { changed ->
                    update { s ->
                        s.copy(variants = s.variants.mapIndexed { i, v -> if (i == index) changed else v })
                    }
                },
                onRemove = {
                    update { s -> s.copy(variants = s.variants.filterIndexed { i, _ -> i != index }) }
                },
            )
        }
        TextButton(onClick = {
            update { s ->
                s.copy(variants = s.variants + VariantDraft(strengthText = s.strengthText))
            }
        }) {
            Text(stringResource(R.string.variant_add))
        }
        if (state.variants.size > 1) {
            Text(
                text = stringResource(R.string.variant_default_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VariantCard(
    index: Int,
    draft: VariantDraft,
    removable: Boolean,
    unit: String,
    onChange: (VariantDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.variant_title, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (removable) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = RemoveIcon,
                            contentDescription = stringResource(R.string.times_remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.strengthText,
                    onValueChange = { onChange(draft.copy(strengthText = it)) },
                    label = { Text(stringResource(R.string.variant_strength) + unitSuffix(unit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.stockText,
                    onValueChange = { onChange(draft.copy(stockText = it)) },
                    label = { Text(stringResource(R.string.variant_stock)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.thresholdText,
                    onValueChange = { onChange(draft.copy(thresholdText = it)) },
                    label = { Text(stringResource(R.string.variant_threshold)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.refillText,
                    onValueChange = { onChange(draft.copy(refillText = it)) },
                    label = { Text(stringResource(R.string.variant_refill)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun unitSuffix(unit: String): String = if (unit.isBlank()) "" else ", $unit"

@Composable
private fun ReviewStep(state: MedEditUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MedIconBox(form = state.form, colorSeed = state.colorSeed)
        Column {
            Text(
                text =
                    listOf(state.name, state.strengthText, state.strengthUnit)
                        .filter { it.isNotBlank() }
                        .joinToString(" "),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.instructions.isNotBlank()) {
                Text(
                    text = state.instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    ReviewBlock(title = stringResource(R.string.review_schedule)) {
        val locale = currentLocale()
        val typeText =
            when (state.scheduleType) {
                ScheduleType.FIXED_TIMES -> {
                    stringResource(R.string.schedule_type_daily)
                }

                ScheduleType.WEEKDAYS -> {
                    state.weekdays
                        .sorted()
                        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
                }

                ScheduleType.EVERY_N_DAYS -> {
                    everyNDaysText(state.intervalText.toIntOrNull() ?: 1)
                }

                ScheduleType.CYCLE -> {
                    stringResource(
                        R.string.schedule_cycle_summary,
                        state.cycleOnText.toIntOrNull() ?: 0,
                        state.cycleOffText.toIntOrNull() ?: 0,
                    )
                }

                ScheduleType.AS_NEEDED -> {
                    stringResource(R.string.schedule_as_needed)
                }
            }
        Text(
            text = typeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.scheduleType != ScheduleType.AS_NEEDED) {
            Text(
                text =
                    state.times
                        .sortedBy { it.time }
                        .joinToString("   ") { "${it.time.format(TimeFormat)} × ${it.amountText}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.trackStock) {
        ReviewBlock(title = stringResource(R.string.review_stock)) {
            state.variants.forEach { draft ->
                Text(
                    text =
                        stringResource(
                            R.string.review_variant_line,
                            draft.strengthText.ifBlank { "—" },
                            state.strengthUnit,
                            draft.stockText.ifBlank { "0" },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ReviewBlock(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

private val RemoveIcon by lazy {
    strokeGlyph("Remove") {
        moveTo(6f, 6f)
        lineToRelative(12f, 12f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }
}
