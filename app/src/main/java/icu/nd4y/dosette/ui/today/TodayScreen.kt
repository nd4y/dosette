package icu.nd4y.dosette.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.domain.nag.SnoozeTarget
import icu.nd4y.dosette.ui.calendar.AddOneOffDialog
import icu.nd4y.dosette.ui.calendar.CalendarPanel
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.common.currentLocale
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.EmptyState
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import icu.nd4y.dosette.ui.designsystem.MedPalette
import icu.nd4y.dosette.ui.designsystem.PressPosition
import icu.nd4y.dosette.ui.designsystem.RingCenterLabel
import icu.nd4y.dosette.ui.designsystem.SegmentedRing
import icu.nd4y.dosette.ui.designsystem.effectsSpec
import icu.nd4y.dosette.ui.designsystem.strokeGlyph
import icu.nd4y.dosette.ui.designsystem.trackPressFor
import icu.nd4y.dosette.ui.theme.LocalDarkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Bumped when the Today tab is re-tapped: back to today. */
    reselectTick: Int = 0,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val messageTemplate = stringResource(R.string.prn_taken_snackbar)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel) {
        viewModel.prnTaken.collect { taken ->
            val result =
                snackbarHost.showSnackbar(
                    message = messageTemplate.format(taken.medicationName),
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoPrn(taken.logId)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        TodayContent(
            state = state,
            contentPadding = contentPadding,
            onTake = viewModel::take,
            onSkip = viewModel::skip,
            onSnooze = viewModel::snooze,
            onUndo = viewModel::undo,
            onDeleteOneOff = viewModel::deleteOneOff,
            onTakePrn = viewModel::takePrn,
            onSelectProfile = viewModel::selectProfile,
            onSelectDate = viewModel::select,
            onGoToday = viewModel::goToday,
            onPreviousDay = viewModel::previousDay,
            onNextDay = viewModel::nextDay,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onShowMonth = viewModel::showMonth,
            onAddOneOff = viewModel::addOneOff,
            reselectTick = reselectTick,
        )
        SnackbarHost(
            hostState = snackbarHost,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }
}

/** Stateless body — rendered directly by screenshot tests. */
@Composable
fun TodayContent(
    state: TodayUiState,
    contentPadding: PaddingValues,
    onTake: (TodayDose) -> Unit,
    onSkip: (TodayDose) -> Unit,
    onSnooze: (TodayDose, SnoozeTarget) -> Unit,
    onUndo: (TodayDose) -> Unit,
    onDeleteOneOff: (TodayDose) -> Unit,
    onTakePrn: (PrnMed) -> Unit,
    onSelectProfile: (String) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onGoToday: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShowMonth: (java.time.YearMonth) -> Unit,
    onAddOneOff: (String, java.time.LocalTime, Double) -> Unit,
    modifier: Modifier = Modifier,
    reselectTick: Int = 0,
    calendarExpandedInitially: Boolean = false,
) {
    var calendarExpanded by rememberSaveable { mutableStateOf(calendarExpandedInitially) }
    var addDialogOpen by remember { mutableStateOf(false) }

    // Re-tapping the Today tab: back to today, calendar folded away.
    LaunchedEffect(reselectTick) {
        if (reselectTick > 0) {
            calendarExpanded = false
            onGoToday()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
    ) {
        DayHeaderRow(
            state = state,
            calendarExpanded = calendarExpanded,
            onToggleCalendar = { calendarExpanded = !calendarExpanded },
            onGoToday = onGoToday,
        )
        ProfileChips(state, onSelectProfile)
        AnimatedVisibility(
            visible = calendarExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            CalendarPanel(
                month = state.month,
                days = state.calendarDays,
                selectedDate = state.selectedDate,
                monthAdherencePercent = state.monthAdherencePercent,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onShowMonth = onShowMonth,
                onSelect = { date ->
                    calendarExpanded = false
                    onSelectDate(date)
                },
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        val dayMotion = effectsSpec<Float>()
        AnimatedContent(
            targetState = state,
            contentKey = { it.selectedDate },
            transitionSpec = {
                // Next day slides in from below (the swipe-up direction),
                // the previous one from above.
                val forward = targetState.selectedDate > initialState.selectedDate
                if (forward) {
                    (slideInVertically { it / 3 } + fadeIn(dayMotion)) togetherWith
                        (slideOutVertically { -it / 3 } + fadeOut(dayMotion))
                } else {
                    (slideInVertically { -it / 3 } + fadeIn(dayMotion)) togetherWith
                        (slideOutVertically { it / 3 } + fadeOut(dayMotion))
                } using SizeTransform(clip = true)
            },
            label = "day",
            modifier = Modifier.weight(1f),
        ) { dayState ->
            DayContent(
                state = dayState,
                onTake = onTake,
                onSkip = onSkip,
                onSnooze = onSnooze,
                onUndo = onUndo,
                onDeleteOneOff = onDeleteOneOff,
                onTakePrn = onTakePrn,
                onSelectDate = onSelectDate,
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay,
                onAddOneOff = { addDialogOpen = true },
            )
        }
    }

    if (addDialogOpen && state.medications.isNotEmpty()) {
        AddOneOffDialog(
            medications = state.medications,
            onConfirm = { medicationId, time, amount ->
                addDialogOpen = false
                onAddOneOff(medicationId, time, amount)
            },
            onDismiss = { addDialogOpen = false },
        )
    }
}

/** «Сегодня ⌄» / «Вчера ⌄» — the day title with the calendar toggle. */
@Composable
private fun DayHeaderRow(
    state: TodayUiState,
    calendarExpanded: Boolean,
    onToggleCalendar: () -> Unit,
    onGoToday: () -> Unit,
) {
    val locale = currentLocale()
    val formatted =
        remember(state.selectedDate, locale) {
            val raw = state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
            if (locale.language == "ru") raw.replaceFirstChar { it.lowercase(locale) } else raw
        }
    val title =
        when (state.selectedDate) {
            state.date -> stringResource(R.string.tab_today)
            state.date.minusDays(1) -> stringResource(R.string.day_yesterday)
            state.date.plusDays(1) -> stringResource(R.string.day_tomorrow)
            else -> formatted
        }
    val subtitle = if (title == formatted) null else formatted
    val chevronAngle by animateFloatAsState(if (calendarExpanded) 180f else 0f, label = "chevron")

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onToggleCalendar),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = ChevronDown,
                    contentDescription = stringResource(R.string.calendar_toggle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).rotate(chevronAngle),
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!state.isToday) {
            TextButton(onClick = onGoToday) {
                Text(stringResource(R.string.back_to_today))
            }
        }
    }
}

/** One day of doses; edge swipes hand over to the neighbouring days. */
@Composable
private fun DayContent(
    state: TodayUiState,
    onTake: (TodayDose) -> Unit,
    onSkip: (TodayDose) -> Unit,
    onSnooze: (TodayDose, SnoozeTarget) -> Unit,
    onUndo: (TodayDose) -> Unit,
    onDeleteOneOff: (TodayDose) -> Unit,
    onTakePrn: (PrnMed) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onAddOneOff: () -> Unit,
) {
    if (state.showEmptyState) {
        EmptyState(
            icon = DosetteIcons.Today,
            title = stringResource(R.string.today_empty_title),
            subtitle = stringResource(R.string.today_empty_subtitle),
        )
        return
    }

    val thresholdPx = with(LocalDensity.current) { DAY_SWITCH_THRESHOLD.toPx() }
    val daySwitch =
        remember(state.selectedDate) {
            DaySwitchConnection(thresholdPx, onPreviousDay, onNextDay)
        }

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(daySwitch),
    ) {
        if (state.isToday && state.unresolvedYesterday) {
            item(key = "carryover-banner") {
                CarryoverBanner(onClick = { onSelectDate(state.date.minusDays(1)) })
            }
        }
        if (state.isToday) {
            item(key = "hero") { HeroCard(state) }
        }
        if (state.doses.isEmpty()) {
            item(key = "day-empty") {
                Text(
                    text = stringResource(R.string.timeline_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        doseItems(state, DoseActions(onTake, onSkip, onSnooze, onUndo, onDeleteOneOff))
        if (state.medications.isNotEmpty()) {
            item(key = "oneoff-add") {
                TextButton(onClick = onAddOneOff) {
                    Text(stringResource(R.string.oneoff_add))
                }
            }
        }
        prnItems(state, onTakePrn)
    }
}

/** The per-dose callbacks bundled, so item builders stay small. */
private class DoseActions(
    val onTake: (TodayDose) -> Unit,
    val onSkip: (TodayDose) -> Unit,
    val onSnooze: (TodayDose, SnoozeTarget) -> Unit,
    val onUndo: (TodayDose) -> Unit,
    val onDeleteOneOff: (TodayDose) -> Unit,
)

private fun LazyListScope.doseItems(
    state: TodayUiState,
    actions: DoseActions,
) {
    val readOnly = state.selectedDate > state.date
    slotSections(state.doses).forEach { doses ->
        item(key = "slot-${doses.first().slot}-${doses.first().time}") {
            SlotHeader(slot = doses.first().slot, doses = doses, modifier = Modifier.padding(top = 10.dp))
        }
        doses.forEach { dose ->
            item(key = "dose-${dose.key.encode()}") {
                DoseItem(
                    dose = dose,
                    readOnly = readOnly,
                    snoozePlaces = state.snoozePlaces,
                    onTake = actions.onTake,
                    onSkip = actions.onSkip,
                    onSnooze = actions.onSnooze,
                    onUndo = actions.onUndo,
                    onDeleteOneOff = actions.onDeleteOneOff,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private fun LazyListScope.prnItems(
    state: TodayUiState,
    onTakePrn: (PrnMed) -> Unit,
) {
    if (!state.isToday || state.prn.isEmpty()) return
    item(key = "prn-header") {
        Text(
            text = stringResource(R.string.today_prn_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    state.prn.forEach { prnMed ->
        item(key = "prn-${prnMed.medicationId}") {
            PrnRow(prnMed = prnMed, onTake = { onTakePrn(prnMed) })
        }
    }
}

/** «За вчера остался неотмеченный приём» — one tap away. */
@Composable
private fun CarryoverBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.unresolved_yesterday_banner),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = DosetteIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun HeroCard(state: TodayUiState) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SegmentedRing(
                total = state.plannedCount,
                done = state.takenCount,
                doneColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
            ) {
                RingCenterLabel(done = state.takenCount, total = state.plannedCount)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.today_taken_of, state.takenCount, state.plannedCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val pending = state.doses.count { it.status == DoseUiStatus.PENDING }
                Text(
                    text =
                        when {
                            state.nextDoseTime != null -> {
                                stringResource(R.string.today_next_dose, state.nextDoseTime.format(TimeFormat))
                            }

                            pending > 0 -> {
                                pluralStringResource(R.plurals.today_pending_left, pending, pending)
                            }

                            else -> {
                                stringResource(R.string.today_all_done)
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SlotHeader(
    slot: DaySlot,
    doses: List<TodayDose>,
    modifier: Modifier = Modifier,
) {
    val label =
        when (slot) {
            DaySlot.MORNING -> stringResource(R.string.slot_morning)
            DaySlot.AFTERNOON -> stringResource(R.string.slot_afternoon)
            DaySlot.EVENING -> stringResource(R.string.slot_evening)
            DaySlot.NIGHT -> stringResource(R.string.slot_night)
        }
    val firstTime = doses.minOf { it.time }.format(TimeFormat)
    // Only genuinely taken doses earn the label; a missed slot stays silent.
    val allTaken = doses.all { it.status == DoseUiStatus.TAKEN }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$label · $firstTime",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (allTaken) {
            Text(
                text = stringResource(R.string.slot_all_done),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DoseItem(
    dose: TodayDose,
    readOnly: Boolean,
    snoozePlaces: Set<PlaceId>,
    onTake: (TodayDose) -> Unit,
    onSkip: (TodayDose) -> Unit,
    onSnooze: (TodayDose, SnoozeTarget) -> Unit,
    onUndo: (TodayDose) -> Unit,
    onDeleteOneOff: (TodayDose) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fade = effectsSpec<Float>()
    AnimatedContent(
        targetState = dose,
        contentKey = { it.status },
        transitionSpec = {
            (fadeIn(fade) togetherWith fadeOut(fade)) using SizeTransform(clip = false)
        },
        label = "dose",
        modifier = modifier,
    ) { animatedDose ->
        when {
            animatedDose.status != DoseUiStatus.PENDING -> {
                ActedDoseRow(
                    dose = animatedDose,
                    onTake = { onTake(animatedDose) },
                    onSkip = { onSkip(animatedDose) },
                    onUndo = { onUndo(animatedDose) },
                )
            }

            readOnly -> {
                PlannedDoseRow(animatedDose)
            }

            else -> {
                PendingDoseCard(
                    dose = animatedDose,
                    snoozePlaces = snoozePlaces,
                    onTake = { onTake(animatedDose) },
                    onSkip = { onSkip(animatedDose) },
                    onSnooze = { target -> onSnooze(animatedDose, target) },
                    onDeleteOneOff = if (animatedDose.oneOff) ({ onDeleteOneOff(animatedDose) }) else null,
                )
            }
        }
    }
}

/** A future dose: same anatomy, no actions — the day has not come yet. */
@Composable
private fun PlannedDoseRow(dose: TodayDose) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        MedIconBox(form = dose.form, colorSeed = dose.colorSeed, size = 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = dose.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    listOfNotNull(dose.strengthText, stringResource(R.string.unit_pieces, dose.amountText))
                        .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = dose.time.format(TimeFormat),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActedDoseRow(
    dose: TodayDose,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val press = remember { PressPosition() }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .trackPressFor(press)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            MedIconBox(form = dose.form, colorSeed = dose.colorSeed, size = 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = dose.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = actedSubtitle(dose),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusCircle(dose.status)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, offset = press.menuOffset) {
            if (dose.status != DoseUiStatus.TAKEN) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mark_taken)) },
                    onClick = {
                        menuOpen = false
                        onTake()
                    },
                )
            }
            if (dose.status != DoseUiStatus.SKIPPED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mark_skipped)) },
                    onClick = {
                        menuOpen = false
                        onSkip()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.undo_mark)) },
                onClick = {
                    menuOpen = false
                    onUndo()
                },
            )
        }
    }
}

@Composable
private fun actedSubtitle(dose: TodayDose): String {
    val doseText =
        listOfNotNull(dose.strengthText, stringResource(R.string.unit_pieces, dose.amountText))
            .joinToString(" · ")
    val statusText =
        when (dose.status) {
            DoseUiStatus.TAKEN -> {
                dose.actedTime?.let { stringResource(R.string.dose_taken_at, it.format(TimeFormat)) }
            }

            DoseUiStatus.SKIPPED -> {
                stringResource(R.string.dose_skipped_label)
            }

            DoseUiStatus.MISSED -> {
                stringResource(R.string.dose_missed_label)
            }

            DoseUiStatus.PENDING -> {
                null
            }
        }
    return listOfNotNull(doseText.ifBlank { null }, statusText).joinToString(" · ")
}

@Composable
private fun StatusCircle(status: DoseUiStatus) {
    val (bg, fg, icon) =
        when (status) {
            DoseUiStatus.TAKEN -> {
                Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, DosetteIcons.Check)
            }

            DoseUiStatus.SKIPPED -> {
                Triple(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    SkipIcon,
                )
            }

            DoseUiStatus.MISSED -> {
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    CrossIcon,
                )
            }

            DoseUiStatus.PENDING -> {
                Triple(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurface,
                    DosetteIcons.Check,
                )
            }
        }
    Box(
        modifier = Modifier.size(28.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ProfileChips(
    state: TodayUiState,
    onSelectProfile: (String) -> Unit,
) {
    if (state.profiles.size < 2) return
    val dark = LocalDarkTheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
    ) {
        state.profiles.forEach { profile ->
            val selected = profile.id == state.activeProfileId
            val palette = MedPalette.resolve(profile.colorSeed, dark)
            Surface(
                onClick = { onSelectProfile(profile.id) },
                shape = RoundedCornerShape(if (selected) 14.dp else 20.dp),
                modifier = Modifier.semantics { this.selected = profile.id == state.activeProfileId },
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(22.dp)
                                .background(palette.container, CircleShape),
                    ) {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onContainer,
                        )
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingDoseCard(
    dose: TodayDose,
    snoozePlaces: Set<PlaceId>,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: (SnoozeTarget) -> Unit,
    onDeleteOneOff: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            MedIconBox(form = dose.form, colorSeed = dose.colorSeed, size = 44.dp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = dose.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        listOfNotNull(
                            dose.strengthText,
                            stringResource(R.string.unit_pieces, dose.amountText),
                            dose.instructions,
                        ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TakeSplitButton(
                snoozePlaces = snoozePlaces,
                snoozable = dose.reminderActive,
                onTake = onTake,
                onSkip = onSkip,
                onSnooze = onSnooze,
                onDeleteOneOff = onDeleteOneOff,
            )
        }
    }
}

@Composable
private fun TakeSplitButton(
    snoozePlaces: Set<PlaceId>,
    snoozable: Boolean,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: (SnoozeTarget) -> Unit,
    onDeleteOneOff: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            onClick = onTake,
            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = stringResource(R.string.action_take),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Box {
            Surface(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(modifier = Modifier.width(34.dp).height(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = ChevronDown,
                        contentDescription = stringResource(R.string.detail_more_actions),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_skip)) },
                    onClick = {
                        menuOpen = false
                        onSkip()
                    },
                )
                // A snooze parks a ringing reminder; for a past or future dose
                // there is nothing to park, so the entries stay away.
                if (snoozable) {
                    SNOOZE_MINUTE_CHOICES.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.snooze_for_min, minutes)) },
                            onClick = {
                                menuOpen = false
                                onSnooze(SnoozeTarget.ForMinutes(minutes))
                            },
                        )
                    }
                    if (PlaceId.HOME in snoozePlaces) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.snooze_until_home)) },
                            onClick = {
                                menuOpen = false
                                onSnooze(SnoozeTarget.UntilPlace(PlaceId.HOME))
                            },
                        )
                    }
                    if (PlaceId.WORK in snoozePlaces) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.snooze_until_work)) },
                            onClick = {
                                menuOpen = false
                                onSnooze(SnoozeTarget.UntilPlace(PlaceId.WORK))
                            },
                        )
                    }
                }
                if (onDeleteOneOff != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.oneoff_delete)) },
                        onClick = {
                            menuOpen = false
                            onDeleteOneOff()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrnRow(
    prnMed: PrnMed,
    onTake: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border =
            androidx.compose.foundation
                .BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            MedIconBox(form = prnMed.form, colorSeed = prnMed.colorSeed, size = 32.dp)
            Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(prnMed.name, prnMed.strengthText).joinToString(" "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.schedule_as_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onTake,
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PlusSmall,
                        contentDescription = stringResource(R.string.prn_take),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private val DAY_SWITCH_THRESHOLD = 96.dp

private val CrossIcon: ImageVector by lazy {
    strokeGlyph("Cross", strokeWidth = 3f) {
        moveTo(6f, 6f)
        lineToRelative(12f, 12f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }
}

private val SkipIcon: ImageVector by lazy {
    strokeGlyph("Skip", strokeWidth = 2.6f) {
        moveTo(6f, 12f)
        lineToRelative(12f, 0f)
    }
}

private val SNOOZE_MINUTE_CHOICES = listOf(10, 30, 60)

private val ChevronDown: ImageVector by lazy {
    strokeGlyph("ChevronDown", strokeWidth = 2.6f) {
        moveTo(6f, 9f)
        lineToRelative(6f, 6f)
        lineToRelative(6f, -6f)
    }
}

private val PlusSmall: ImageVector by lazy {
    strokeGlyph("Plus", strokeWidth = 2.4f) {
        moveTo(12f, 5f)
        lineToRelative(0f, 14f)
        moveTo(5f, 12f)
        lineToRelative(14f, 0f)
    }
}
