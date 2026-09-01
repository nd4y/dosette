package icu.nd4y.dosette.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.stats.AdherenceCalculator.DayStatus
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.common.currentLocale
import icu.nd4y.dosette.ui.common.formatAmount
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import icu.nd4y.dosette.ui.designsystem.rememberDirectionalMotion
import icu.nd4y.dosette.ui.designsystem.strokeGlyph
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.TodayDose
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun CalendarScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarContent(
        state = state,
        contentPadding = contentPadding,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onShowMonth = viewModel::showMonth,
        onSelect = viewModel::select,
        onMark = viewModel::mark,
        onUndo = viewModel::undo,
        onAddOneOff = viewModel::addOneOff,
        onDeleteOneOff = viewModel::deleteOneOff,
        modifier = modifier,
    )
}

/** Stateless body — rendered directly by screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarContent(
    state: CalendarUiState,
    contentPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShowMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onMark: (TodayDose, Boolean) -> Unit,
    onUndo: (TodayDose) -> Unit,
    onAddOneOff: (String, LocalDate, LocalTime, Double) -> Unit,
    onDeleteOneOff: (TodayDose) -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthPickerOpen by remember { mutableStateOf(false) }

    // The month grid sits at the BOTTOM, in one-handed thumb reach; the
    // selected day's details and the month summary scroll above it.
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
    ) {
        CalendarHeader(state, onPreviousMonth, onNextMonth, onTitleClick = { monthPickerOpen = true })
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            DayPanel(
                date = state.selectedDate,
                doses = state.selectedDoses,
                medications = state.medications,
                onMark = onMark,
                onUndo = onUndo,
                onAddOneOff = onAddOneOff,
                onDeleteOneOff = onDeleteOneOff,
            )
            AdherenceCard(state)
        }
        Legend()
        WeekdayRow()
        val monthMotion = rememberDirectionalMotion()
        AnimatedContent(
            targetState = state,
            contentKey = { it.month },
            transitionSpec = { monthMotion.transform(forward = targetState.month > initialState.month) },
            label = "month",
            modifier =
                Modifier
                    .padding(bottom = 8.dp)
                    .pointerInput(Unit) {
                        // Horizontal swipe between months; threshold keeps taps intact.
                        var dragTotal = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onDragEnd = {
                                when {
                                    dragTotal > SWIPE_THRESHOLD_PX -> onPreviousMonth()
                                    dragTotal < -SWIPE_THRESHOLD_PX -> onNextMonth()
                                }
                            },
                        ) { _, dragAmount -> dragTotal += dragAmount }
                    },
        ) { monthState ->
            MonthGrid(monthState, onSelect)
        }
    }

    if (monthPickerOpen) {
        MonthPickerDialog(
            current = state.month,
            onPick = { picked ->
                monthPickerOpen = false
                onShowMonth(picked)
            },
            onDismiss = { monthPickerOpen = false },
        )
    }
}

@Composable
private fun CalendarHeader(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTitleClick: () -> Unit,
) {
    val locale = currentLocale()
    val monthTitle =
        remember(state.month, locale) {
            state.month
                .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                .replaceFirstChar { it.titlecase(locale) }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.tab_calendar),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPreviousMonth) {
            Icon(
                ChevronLeft,
                contentDescription = stringResource(R.string.calendar_prev_month),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = monthTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onTitleClick)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
        )
        IconButton(onClick = onNextMonth) {
            Icon(
                DosetteIcons.ChevronRight,
                contentDescription = stringResource(R.string.calendar_next_month),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthPickerDialog(
    current: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    var year by remember { mutableIntStateOf(current.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) {
                    Icon(
                        ChevronLeft,
                        contentDescription = stringResource(R.string.calendar_prev_year),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { year++ }) {
                    Icon(
                        DosetteIcons.ChevronRight,
                        contentDescription = stringResource(R.string.calendar_next_year),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                java.time.Month.entries.chunked(MONTHS_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { month ->
                            val selected = year == current.year && month == current.month
                            Surface(
                                onClick = { onPick(YearMonth.of(year, month)) },
                                shape = MaterialTheme.shapes.medium,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text =
                                        month
                                            .getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                                            .replaceFirstChar { it.titlecase(locale) },
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MONTHS_PER_ROW = 3
private const val SWIPE_THRESHOLD_PX = 120f

@Composable
private fun WeekdayRow() {
    val locale = currentLocale()
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    state: CalendarUiState,
    onSelect: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.days.chunked(7).forEach { week ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        DayCell(
                            day = day,
                            selected = day.date == state.selectedDate,
                            onClick = { onSelect(day.date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // The selection fill (the day whose details fill the panel below)
    // replaces the status ring; today's own fill outranks both.
    val fill = dayCellFill(day, selected)
    val ring = if (fill == Color.Transparent) dayStatusColor(day.status) else Color.Transparent
    val base =
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(fill, CircleShape)
    val decorated = if (ring != Color.Transparent) base.border(3.dp, ring, CircleShape) else base
    Box(modifier = decorated, contentAlignment = Alignment.Center) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (day.isToday || day.status != null) FontWeight.SemiBold else FontWeight.Normal,
            color = dayCellTextColor(day, selected),
        )
    }
}

@Composable
private fun dayCellFill(
    day: CalendarDay,
    selected: Boolean,
): Color =
    when {
        day.isToday -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }

@Composable
private fun dayStatusColor(status: DayStatus?): Color =
    when (status) {
        DayStatus.COMPLETE -> MaterialTheme.colorScheme.primary
        DayStatus.PARTIAL -> PartialColor
        DayStatus.ALL_MISSED -> MaterialTheme.colorScheme.error
        DayStatus.NONE, null -> Color.Transparent
    }

@Composable
private fun dayCellTextColor(
    day: CalendarDay,
    selected: Boolean,
): Color =
    when {
        day.isToday -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        !day.inMonth -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

@Composable
private fun Legend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        LegendItem(color = MaterialTheme.colorScheme.primary, ring = true, label = stringResource(R.string.legend_full))
        LegendItem(color = PartialColor, ring = true, label = stringResource(R.string.legend_partial))
        LegendItem(color = MaterialTheme.colorScheme.error, ring = true, label = stringResource(R.string.legend_missed))
        LegendItem(
            color = MaterialTheme.colorScheme.primary,
            ring = false,
            label = stringResource(R.string.legend_today),
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    ring: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier =
                Modifier.size(12.dp).let {
                    if (ring) it.border(3.dp, color, CircleShape) else it.background(color, CircleShape)
                },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdherenceCard(state: CalendarUiState) {
    val locale = currentLocale()
    val monthName =
        remember(state.month, locale) {
            val name = state.month.format(DateTimeFormatter.ofPattern("LLLL", locale))
            // Lowercase inline month names is a Russian-only convention.
            if (locale.language == "ru") name.lowercase(locale) else name
        }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.calendar_month_adherence, monthName),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.monthAdherencePercent?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DayPanel(
    date: LocalDate,
    doses: List<TodayDose>,
    medications: List<OneOffMedOption>,
    onMark: (TodayDose, Boolean) -> Unit,
    onUndo: (TodayDose) -> Unit,
    onAddOneOff: (String, LocalDate, LocalTime, Double) -> Unit,
    onDeleteOneOff: (TodayDose) -> Unit,
) {
    val locale = currentLocale()
    val title =
        remember(date, locale) {
            date
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
                .replaceFirstChar { it.titlecase(locale) }
        }
    var addDialogOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (doses.isEmpty()) {
                Text(
                    text = stringResource(R.string.timeline_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            doses.forEach { dose ->
                DaySheetRow(dose = dose, onMark = onMark, onUndo = onUndo, onDeleteOneOff = onDeleteOneOff)
            }
            if (medications.isNotEmpty()) {
                TextButton(onClick = { addDialogOpen = true }) {
                    Text(stringResource(R.string.oneoff_add))
                }
            }
        }
    }
    if (addDialogOpen) {
        AddOneOffDialog(
            medications = medications,
            onConfirm = { medicationId, time, amount ->
                addDialogOpen = false
                onAddOneOff(medicationId, date, time, amount)
            },
            onDismiss = { addDialogOpen = false },
        )
    }
}

@Composable
private fun DaySheetRow(
    dose: TodayDose,
    onMark: (TodayDose, Boolean) -> Unit,
    onUndo: (TodayDose) -> Unit,
    onDeleteOneOff: (TodayDose) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        MedIconBox(form = dose.form, colorSeed = dose.colorSeed, size = 30.dp)
        Text(
            text = "${dose.name} · ${dose.time.format(TimeFormat)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                when (dose.status) {
                    DoseUiStatus.TAKEN -> {
                        dose.actedTime?.let { stringResource(R.string.dose_taken_at, it.format(TimeFormat)) }
                            ?: stringResource(R.string.dose_taken_at, "—")
                    }

                    DoseUiStatus.SKIPPED -> {
                        stringResource(R.string.dose_skipped_label)
                    }

                    DoseUiStatus.MISSED -> {
                        stringResource(R.string.dose_missed_label)
                    }

                    DoseUiStatus.PENDING -> {
                        "—"
                    }
                },
            style = MaterialTheme.typography.labelMedium,
            color =
                when (dose.status) {
                    DoseUiStatus.MISSED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = MoreDots,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mark_taken)) },
                    onClick = {
                        menuOpen = false
                        onMark(dose, true)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mark_skipped)) },
                    onClick = {
                        menuOpen = false
                        onMark(dose, false)
                    },
                )
                if (dose.status != DoseUiStatus.PENDING) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.undo_mark)) },
                        onClick = {
                            menuOpen = false
                            onUndo(dose)
                        },
                    )
                }
                if (dose.oneOff) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.oneoff_delete)) },
                        onClick = {
                            menuOpen = false
                            onDeleteOneOff(dose)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOneOffDialog(
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

// Amber "partial" ring from the mockups; deliberately theme-independent.
private val PartialColor = Color(0xFFE8A33D)

private val ChevronLeft: ImageVector by lazy {
    strokeGlyph("ChevronLeft") {
        moveTo(15f, 6f)
        lineToRelative(-6f, 6f)
        lineToRelative(6f, 6f)
    }
}

private val MoreDots: ImageVector by lazy {
    strokeGlyph("MoreDots", strokeWidth = 2.4f) {
        moveTo(12f, 5.5f)
        lineToRelative(0f, 0.01f)
        moveTo(12f, 12f)
        lineToRelative(0f, 0.01f)
        moveTo(12f, 18.5f)
        lineToRelative(0f, 0.01f)
    }
}
