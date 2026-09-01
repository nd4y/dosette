package icu.nd4y.dosette.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.stats.AdherenceCalculator.DayStatus
import icu.nd4y.dosette.ui.common.currentLocale
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.rememberDirectionalMotion
import icu.nd4y.dosette.ui.designsystem.strokeGlyph
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    /** null = nothing recorded (future or empty day). */
    val status: DayStatus?,
)

/** Medication offered in the one-off dose dialog. */
data class OneOffMedOption(
    val id: String,
    val name: String,
    val form: MedicationForm,
    val colorSeed: Int,
    val defaultAmount: Double,
)

/**
 * The collapsible month view of the merged Today screen: month
 * navigation, the status grid, the legend and the month's adherence.
 * Tapping a day hands the date back and the panel's owner closes it.
 */
@Composable
fun CalendarPanel(
    month: YearMonth,
    days: List<CalendarDay>,
    selectedDate: LocalDate,
    monthAdherencePercent: Int?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShowMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthPickerOpen by remember { mutableStateOf(false) }
    val locale = currentLocale()
    val monthTitle =
        remember(month, locale) {
            month
                .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                .replaceFirstChar { it.titlecase(locale) }
        }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { monthPickerOpen = true }
                            .padding(vertical = 4.dp),
                )
                IconButton(onClick = onNextMonth) {
                    Icon(
                        DosetteIcons.ChevronRight,
                        contentDescription = stringResource(R.string.calendar_next_month),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WeekdayRow()
            val monthMotion = rememberDirectionalMotion()
            AnimatedContent(
                targetState = GridSnapshot(month, days, selectedDate),
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
            ) { snapshot ->
                MonthGrid(snapshot, onSelect)
            }
            Legend()
            monthAdherencePercent?.let { percent ->
                Text(
                    text = "${stringResource(
                        R.string.calendar_month_adherence,
                        adherenceMonthName(month),
                    )} · $percent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (monthPickerOpen) {
        MonthPickerDialog(
            current = month,
            onPick = { picked ->
                monthPickerOpen = false
                onShowMonth(picked)
            },
            onDismiss = { monthPickerOpen = false },
        )
    }
}

@Composable
private fun adherenceMonthName(month: YearMonth): String {
    val locale = currentLocale()
    return remember(month, locale) {
        val name = month.format(DateTimeFormatter.ofPattern("LLLL", locale))
        // Lowercase inline month names is a Russian-only convention.
        if (locale.language == "ru") name.lowercase(locale) else name
    }
}

private data class GridSnapshot(
    val month: YearMonth,
    val days: List<CalendarDay>,
    val selected: LocalDate,
)

@Composable
private fun WeekdayRow() {
    val locale = currentLocale()
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    snapshot: GridSnapshot,
    onSelect: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        snapshot.days.chunked(7).forEach { week ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        DayCell(
                            day = day,
                            selected = day.date == snapshot.selected,
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
    // The selection fill (the day filling the screen below) replaces the
    // status ring; today's own fill outranks both.
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
                    textAlign = TextAlign.Center,
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
                                    textAlign = TextAlign.Center,
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

// Amber "partial" ring from the mockups; deliberately theme-independent.
private val PartialColor = Color(0xFFE8A33D)

private val ChevronLeft: ImageVector by lazy {
    strokeGlyph("ChevronLeft") {
        moveTo(15f, 6f)
        lineToRelative(-6f, 6f)
        lineToRelative(6f, 6f)
    }
}
