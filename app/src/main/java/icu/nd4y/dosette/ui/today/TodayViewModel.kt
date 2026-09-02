package icu.nd4y.dosette.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.repository.ProfileRepository
import icu.nd4y.dosette.data.repository.ReminderStateRepository
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleTime
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.domain.nag.SnoozeTarget
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator
import icu.nd4y.dosette.domain.stats.AdherenceCalculator
import icu.nd4y.dosette.reminders.PrnIntakes
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.UserDoseAction
import icu.nd4y.dosette.reminders.WidgetRefresher
import icu.nd4y.dosette.ui.calendar.CalendarDay
import icu.nd4y.dosette.ui.calendar.OneOffMedOption
import icu.nd4y.dosette.ui.common.dayTicker
import icu.nd4y.dosette.ui.common.strengthLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

enum class DaySlot { MORNING, AFTERNOON, EVENING, NIGHT }

enum class DoseUiStatus { PENDING, TAKEN, SKIPPED, MISSED }

data class TodayDose(
    val medicationId: String,
    val date: LocalDate,
    val time: LocalTime,
    val name: String,
    val strengthText: String?,
    val amountText: String,
    val instructions: String?,
    val form: MedicationForm,
    val colorSeed: Int,
    val status: DoseUiStatus,
    val actedTime: LocalTime?,
    val scheduleId: String? = null,
    /** Single-day schedule created from the calendar — deletable as a whole. */
    val oneOff: Boolean = false,
    /** A reminder is currently ringing for it — the only time a snooze means anything. */
    val reminderActive: Boolean = false,
) {
    val key: OccurrenceKey get() = OccurrenceKey(medicationId, date, time)
    val slot: DaySlot
        get() =
            when (time.hour) {
                in 5..11 -> DaySlot.MORNING
                in 12..17 -> DaySlot.AFTERNOON
                in 18..22 -> DaySlot.EVENING
                else -> DaySlot.NIGHT
            }
}

data class PrnMed(
    val medicationId: String,
    val name: String,
    val strengthText: String?,
    val form: MedicationForm,
    val colorSeed: Int,
)

data class ProfileChip(
    val id: String,
    val name: String,
    val colorSeed: Int,
)

/** One PRN intake just recorded — offered for undo via the snackbar. */
data class PrnTaken(
    val logId: String,
    val medicationName: String,
)

/**
 * The merged Today+Calendar screen: one day at a time (selected from the
 * collapsible month grid or by edge swipes), today by default.
 */
data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    /** The day whose doses fill the screen. */
    val selectedDate: LocalDate = LocalDate.now(),
    val doses: List<TodayDose> = emptyList(),
    /** Yesterday still has an unresolved dose — surfaced as a banner. */
    val unresolvedYesterday: Boolean = false,
    val prn: List<PrnMed> = emptyList(),
    val takenCount: Int = 0,
    val plannedCount: Int = 0,
    val nextDoseTime: LocalTime? = null,
    /** Places configured well enough to snooze until. */
    val snoozePlaces: Set<PlaceId> = emptySet(),
    /** Shown as switcher chips only when more than one exists. */
    val profiles: List<ProfileChip> = emptyList(),
    val activeProfileId: String? = null,
    // The collapsible calendar panel.
    val month: YearMonth = YearMonth.now(),
    /** Always full weeks, Monday-first. */
    val calendarDays: List<CalendarDay> = emptyList(),
    val monthAdherencePercent: Int? = null,
    /** Active medications a one-off dose can be added for. */
    val medications: List<OneOffMedOption> = emptyList(),
) {
    val isToday: Boolean get() = selectedDate == date

    /** Nothing to show at all — no meds, no doses, no as-needed list. */
    val showEmptyState: Boolean
        get() = !loading && doses.isEmpty() && prn.isEmpty() && medications.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val doseLogRepository: DoseLogRepository,
        private val settingsRepository: SettingsRepository,
        private val profileRepository: ProfileRepository,
        private val reminderStateRepository: ReminderStateRepository,
        private val engine: ReminderEngine,
        private val prnIntakes: PrnIntakes,
        private val widgetRefresher: WidgetRefresher,
        private val clock: Clock,
    ) : ViewModel() {
        // Snackbars display sequentially for seconds each; a buffer of one
        // would silently drop quick consecutive intakes.
        private val _prnTaken = MutableSharedFlow<PrnTaken>(extraBufferCapacity = 16)

        /** Fires after each PRN intake so the screen can offer an undo snackbar. */
        val prnTaken: SharedFlow<PrnTaken> = _prnTaken

        /** null = follow today across midnight. */
        private val selected = MutableStateFlow<LocalDate?>(null)

        /** null = the month of the selected day; set while browsing the grid. */
        private val monthOverride = MutableStateFlow<YearMonth?>(null)

        val uiState: StateFlow<TodayUiState> =
            combine(
                settingsRepository.settings,
                dayTicker(clock),
                selected,
                monthOverride,
            ) { settings, today, sel, monthOv ->
                Inputs(
                    activeProfileId = settings.activeProfileId,
                    places = settings.places,
                    today = today,
                    selected = sel ?: today,
                    month = monthOv ?: YearMonth.from(sel ?: today),
                )
            }.distinctUntilChanged()
                .flatMapLatest { inputs -> observeWorld(inputs) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

        private fun observeWorld(inputs: Inputs): kotlinx.coroutines.flow.Flow<TodayUiState> {
            val profileId =
                inputs.activeProfileId
                    ?: return flowOf(
                        TodayUiState(loading = false, date = inputs.today, selectedDate = inputs.selected),
                    )
            val gridStart = gridStart(inputs.month)
            val gridEnd = gridStart.plusDays(GRID_DAYS - 1L)
            val from = minOf(gridStart, inputs.selected, inputs.today.minusDays(1))
            val to = maxOf(gridEnd, inputs.selected, inputs.today)
            return combine(
                medicationRepository.observeByProfile(profileId),
                doseLogRepository.observeRange(profileId, from, to),
                profileRepository.observeAll(),
                reminderStateRepository.observeAll(),
            ) { meds, logs, profiles, states ->
                buildState(inputs, meds, logs, activeReminderKeys(states)).copy(
                    snoozePlaces =
                        inputs.places
                            .filterValues { it.isConfigured }
                            .keys,
                    profiles = profiles.map { ProfileChip(it.id, it.name, it.colorSeed) },
                    activeProfileId = profileId,
                )
            }
        }

        private fun buildState(
            inputs: Inputs,
            meds: List<MedicationDetails>,
            logs: List<DoseLog>,
            activeReminders: Set<OccurrenceKey>,
        ): TodayUiState {
            val today = inputs.today
            val active = meds.filter { !it.medication.isArchived }
            val doses = buildDayDoses(inputs.selected, meds, logs, clock.zone, activeReminders)
            val todayDoses =
                if (inputs.selected == today) doses else buildDayDoses(today, meds, logs, clock.zone)

            val prn =
                active
                    .filter { med -> med.schedules.any { it.endDate == null && it.type == ScheduleType.AS_NEEDED } }
                    .map { med ->
                        PrnMed(
                            medicationId = med.medication.id,
                            name = med.medication.name,
                            strengthText = strengthLabel(med.medication.strengthValue, med.medication.strengthUnit),
                            form = med.medication.form,
                            colorSeed = med.medication.colorSeed,
                        )
                    }

            val now = clock.instant().atZone(clock.zone).toLocalTime()
            return TodayUiState(
                loading = false,
                date = today,
                selectedDate = inputs.selected,
                doses = doses,
                unresolvedYesterday =
                    buildDayDoses(today.minusDays(1), meds, logs, clock.zone)
                        .any { it.status == DoseUiStatus.PENDING },
                prn = prn,
                takenCount = todayDoses.count { it.status == DoseUiStatus.TAKEN },
                plannedCount = todayDoses.size,
                nextDoseTime =
                    todayDoses
                        .filter { it.status == DoseUiStatus.PENDING && it.time >= now }
                        .minOfOrNull { it.time },
                month = inputs.month,
                calendarDays = calendarDays(inputs, logs),
                monthAdherencePercent = monthAdherence(inputs.month, logs),
                medications =
                    active.map { med ->
                        OneOffMedOption(
                            id = med.medication.id,
                            name = med.medication.name,
                            form = med.medication.form,
                            colorSeed = med.medication.colorSeed,
                            defaultAmount =
                                med.schedules
                                    .firstOrNull { it.endDate == null }
                                    ?.defaultDoseAmount ?: 1.0,
                        )
                    },
            )
        }

        private fun calendarDays(
            inputs: Inputs,
            logs: List<DoseLog>,
        ): List<CalendarDay> {
            val gridStart = gridStart(inputs.month)
            val countsByDate =
                logs
                    .filter { it.kind == DoseKind.SCHEDULED }
                    .groupBy { it.date }
            return (0 until GRID_DAYS).map { offset ->
                val date = gridStart.plusDays(offset.toLong())
                val dayLogs = countsByDate[date].orEmpty()
                CalendarDay(
                    date = date,
                    inMonth = YearMonth.from(date) == inputs.month,
                    isToday = date == inputs.today,
                    status =
                        if (dayLogs.isEmpty()) {
                            null
                        } else {
                            AdherenceCalculator.dayStatus(
                                taken = dayLogs.count { it.status == DoseStatus.TAKEN },
                                skipped = dayLogs.count { it.status == DoseStatus.SKIPPED },
                                missed = dayLogs.count { it.status == DoseStatus.MISSED },
                            )
                        },
                )
            }
        }

        private fun monthAdherence(
            month: YearMonth,
            logs: List<DoseLog>,
        ): Int? {
            val monthLogs =
                logs.filter { it.kind == DoseKind.SCHEDULED && YearMonth.from(it.date) == month }
            return AdherenceCalculator.percent(
                taken = monthLogs.count { it.status == DoseStatus.TAKEN },
                missed = monthLogs.count { it.status == DoseStatus.MISSED },
            )
        }

        /** Show a specific day; the grid follows to its month. */
        fun select(date: LocalDate) {
            // Today stays "no selection" so the screen keeps following midnight.
            selected.value = date.takeIf { it != LocalDate.now(clock) }
            monthOverride.value = null
        }

        /** Back to today (tab re-tap, the «today» chip, midnight follow). */
        fun goToday() {
            selected.value = null
            monthOverride.value = null
        }

        /** Edge swipe up at the end of the list. */
        fun nextDay() = select(uiState.value.selectedDate.plusDays(1))

        /** Edge swipe down at the top of the list. */
        fun previousDay() = select(uiState.value.selectedDate.minusDays(1))

        fun previousMonth() = showMonth(uiState.value.month.minusMonths(1))

        fun nextMonth() = showMonth(uiState.value.month.plusMonths(1))

        /** Browse the grid without changing the shown day. */
        fun showMonth(target: YearMonth) {
            monthOverride.value = target
        }

        fun snooze(
            dose: TodayDose,
            target: SnoozeTarget,
        ) {
            viewModelScope.launch { engine.snooze(dose.key, target) }
        }

        fun selectProfile(id: String) {
            viewModelScope.launch {
                settingsRepository.setActiveProfileId(id)
                // Glance sessions expire; without an explicit refresh the
                // widget keeps showing the previous profile until the next
                // alarm fires.
                widgetRefresher.refresh()
            }
        }

        fun take(dose: TodayDose) {
            viewModelScope.launch { engine.onUserAction(dose.key, UserDoseAction.TAKE) }
        }

        fun skip(dose: TodayDose) {
            viewModelScope.launch { engine.onUserAction(dose.key, UserDoseAction.SKIP) }
        }

        /** Revert an accidental Take/Skip mark; the dose becomes pending again. */
        fun undo(dose: TodayDose) {
            viewModelScope.launch { engine.undoDose(dose.key) }
        }

        fun takePrn(prnMed: PrnMed) {
            viewModelScope.launch {
                // The shared path adds the low-stock check and widget refresh.
                val intake = prnIntakes.take(prnMed.medicationId) ?: return@launch
                _prnTaken.tryEmit(PrnTaken(logId = intake.logId, medicationName = intake.medicationName))
            }
        }

        /** Snackbar undo: removes the PRN log and returns the consumed stock. */
        fun undoPrn(logId: String) {
            viewModelScope.launch { prnIntakes.undo(logId) }
        }

        /**
         * One-off dose for the shown day: a single-day FIXED_TIMES schedule
         * version, so the whole reminder/statistics pipeline treats it like
         * any other planned intake.
         */
        fun addOneOff(
            medicationId: String,
            time: LocalTime,
            amount: Double,
        ) {
            val date = uiState.value.selectedDate
            viewModelScope.launch {
                // Occurrence identity is (medication, date, time): a one-off
                // landing exactly on an existing slot would merge with it, so
                // nudge the time forward to the nearest free minute.
                val busy =
                    medicationRepository
                        .getDetails(medicationId)
                        ?.let { details ->
                            OccurrenceGenerator
                                .occurrencesOn(details.schedulesActiveOn(date), date)
                                .map { it.time }
                        }.orEmpty()
                        .toSet()
                var slot = time
                while (slot in busy) slot = slot.plusMinutes(1)
                val scheduleId = UUID.randomUUID().toString()
                medicationRepository.addSchedule(
                    Schedule(
                        id = scheduleId,
                        medicationId = medicationId,
                        type = ScheduleType.FIXED_TIMES,
                        startDate = date,
                        endDate = date,
                        oneOff = true,
                        weekdays = emptySet(),
                        intervalDays = null,
                        cycleDaysOn = null,
                        cycleDaysOff = null,
                        defaultDoseAmount = amount,
                        remindersEnabled = true,
                        createdAt = clock.instant(),
                        times =
                            listOf(
                                ScheduleTime(
                                    id = UUID.randomUUID().toString(),
                                    scheduleId = scheduleId,
                                    time = slot,
                                    doseAmount = amount,
                                    sortIndex = 0,
                                ),
                            ),
                    ),
                )
                engine.reschedule()
            }
        }

        /** Delete a one-off dose together with its log and notifications. */
        fun deleteOneOff(dose: TodayDose) {
            val scheduleId = dose.scheduleId ?: return
            viewModelScope.launch { engine.deleteOneOffSchedule(dose.medicationId, scheduleId) }
        }

        /** Only what the world depends on: any other settings write must not restart the Room flows. */
        private data class Inputs(
            val activeProfileId: String?,
            val places: Map<PlaceId, PlaceConfig>,
            val today: LocalDate,
            val selected: LocalDate,
            val month: YearMonth,
        )

        private companion object {
            const val GRID_DAYS = 42

            /** Keys with a ringing reminder; the flow also refreshes the hero the moment a dose comes due. */
            fun activeReminderKeys(states: List<ReminderState>): Set<OccurrenceKey> =
                states.filter { it.phase == ReminderPhase.ACTIVE }.mapTo(HashSet()) { it.occurrenceKey }

            fun gridStart(month: YearMonth): LocalDate {
                val first = month.atDay(1)
                val shift = (first.dayOfWeek.value + 6) % 7 // Monday-first.
                return first.minusDays(shift.toLong())
            }
        }
    }
