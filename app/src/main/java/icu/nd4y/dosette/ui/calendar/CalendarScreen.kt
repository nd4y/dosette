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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.stats.AdherenceCalculator.DayStatus
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.common.currentLocale
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import icu.nd4y.dosette.ui.designsystem.rememberDirectionalMotion
import icu.nd4y.dosette.ui.designsystem.strokeGlyph
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.TodayDose
import java.time.DayOfWeek
import java.time.LocalDate
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
    onSelect: (LocalDate?) -> Unit,
    onMark: (TodayDose, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthPickerOpen by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        CalendarHeader(state, onPreviousMonth, onNextMonth, onTitleClick = { monthPickerOpen = true })
        WeekdayRow()
        val monthMotion = rememberDirectionalMotion()
        AnimatedContent(
            targetState = state,
            contentKey = { it.month },
            transitionSpec = { monthMotion.transform(forward = targetState.month > initialState.month) },
            label = "month",
            modifier =
                Modifier.pointerInput(Unit) {
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
        Legend()
        AdherenceCard(state)
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

    if (state.selectedDate != null) {
        ModalBottomSheet(onDismissRequest = { onSelect(null) }) {
            DaySheet(date = state.selectedDate, doses = state.selectedDoses, onMark = onMark)
        }
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
                ChevronRight,
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
                        ChevronRight,
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
    onSelect: (LocalDate?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.days.chunked(7).forEach { week ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        DayCell(day = day, onClick = { onSelect(day.date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    onClick: () -> Unit,
) {
    val ringColor =
        when (day.status) {
            DayStatus.COMPLETE -> MaterialTheme.colorScheme.primary
            DayStatus.PARTIAL -> PartialColor
            DayStatus.ALL_MISSED -> MaterialTheme.colorScheme.error
            DayStatus.NONE, null -> Color.Transparent
        }
    val base =
        Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
    val decorated =
        when {
            day.isToday -> base.background(MaterialTheme.colorScheme.primary, CircleShape)
            ringColor != Color.Transparent -> base.border(3.dp, ringColor, CircleShape)
            else -> base
        }
    Box(modifier = decorated, contentAlignment = Alignment.Center) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (day.isToday || day.status != null) FontWeight.SemiBold else FontWeight.Normal,
            color =
                when {
                    day.isToday -> MaterialTheme.colorScheme.onPrimary
                    !day.inMonth -> MaterialTheme.colorScheme.outlineVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
    }
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
            state.month.format(DateTimeFormatter.ofPattern("LLLL", locale)).lowercase(locale)
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
private fun DaySheet(
    date: LocalDate,
    doses: List<TodayDose>,
    onMark: (TodayDose, Boolean) -> Unit,
) {
    val locale = currentLocale()
    val title =
        remember(date, locale) {
            date
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
                .replaceFirstChar { it.titlecase(locale) }
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (doses.isEmpty()) {
            Text(
                text = stringResource(R.string.today_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        doses.forEach { dose -> DaySheetRow(dose = dose, onMark = onMark) }
    }
}

@Composable
private fun DaySheetRow(
    dose: TodayDose,
    onMark: (TodayDose, Boolean) -> Unit,
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
            }
        }
    }
}

// Amber "partial" ring from the mockups; deliberately theme-independent.
private val PartialColor = Color(0xFFE8A33D)

private val ChevronLeft: ImageVector by lazy {
    strokeGlyph("ChevronLeft") {
        moveTo(15f, 6f)
        lineToRelative(-6f, 6f)
        lineToRelative(6f, 6f)
    }
}

private val ChevronRight: ImageVector by lazy {
    strokeGlyph("ChevronRight") {
        moveTo(9f, 6f)
        lineToRelative(6f, 6f)
        lineToRelative(-6f, 6f)
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
