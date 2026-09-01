package icu.nd4y.dosette.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleTime
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator
import icu.nd4y.dosette.domain.stats.AdherenceCalculator
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.UserDoseAction
import icu.nd4y.dosette.ui.common.dayTicker
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.ui.today.buildDayDoses
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    /** null = nothing recorded (future or empty day). */
    val status: AdherenceCalculator.DayStatus?,
)

/** Medication offered in the one-off dose dialog. */
data class OneOffMedOption(
    val id: String,
    val name: String,
    val form: MedicationForm,
    val colorSeed: Int,
    val defaultAmount: Double,
)

data class CalendarUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    /** Always full weeks, Monday-first. */
    val days: List<CalendarDay> = emptyList(),
    val monthAdherencePercent: Int? = null,
    /** The day whose details fill the panel under the grid; today until tapped. */
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDoses: List<TodayDose> = emptyList(),
    /** Active medications a one-off dose can be added for. */
    val medications: List<OneOffMedOption> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val doseLogRepository: DoseLogRepository,
        settingsRepository: SettingsRepository,
        private val engine: ReminderEngine,
        private val clock: Clock,
    ) : ViewModel() {
        private val month = MutableStateFlow(YearMonth.now(clock))
        private val selectedDate = MutableStateFlow<LocalDate?>(null)

        val uiState: StateFlow<CalendarUiState> =
            settingsRepository.settings
                .map { it.activeProfileId }
                .distinctUntilChanged()
                .flatMapLatest { profileId ->
                    if (profileId == null) {
                        flowOf(CalendarUiState(loading = false))
                    } else {
                        month.flatMapLatest { currentMonth ->
                            val gridStart = gridStart(currentMonth)
                            val gridEnd = gridStart.plusDays(GRID_DAYS - 1L)
                            combine(
                                medicationRepository.observeByProfile(profileId),
                                doseLogRepository.observeRange(profileId, gridStart, gridEnd),
                                selectedDate,
                                // Moves the "today" ring at midnight without
                                // waiting for a data change.
                                dayTicker(clock),
                            ) { meds, logs, selectedOrNull, today ->
                                val selected = selectedOrNull ?: today
                                val countsByDate =
                                    logs
                                        .filter { it.kind == DoseKind.SCHEDULED }
                                        .groupBy { it.date }
                                val days =
                                    (0 until GRID_DAYS).map { offset ->
                                        val date = gridStart.plusDays(offset.toLong())
                                        val dayLogs = countsByDate[date].orEmpty()
                                        CalendarDay(
                                            date = date,
                                            inMonth = YearMonth.from(date) == currentMonth,
                                            isToday = date == today,
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
                                val monthLogs =
                                    countsByDate
                                        .filterKeys { YearMonth.from(it) == currentMonth }
                                        .values
                                        .flatten()
                                CalendarUiState(
                                    loading = false,
                                    month = currentMonth,
                                    days = days,
                                    monthAdherencePercent =
                                        AdherenceCalculator.percent(
                                            taken = monthLogs.count { it.status == DoseStatus.TAKEN },
                                            missed = monthLogs.count { it.status == DoseStatus.MISSED },
                                        ),
                                    selectedDate = selected,
                                    selectedDoses = buildDayDoses(selected, meds, logs, clock.zone),
                                    medications =
                                        meds
                                            .filter { !it.medication.isArchived }
                                            .map { med ->
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
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

        fun previousMonth() = showMonth(month.value.minusMonths(1))

        fun nextMonth() = showMonth(month.value.plusMonths(1))

        fun showMonth(target: YearMonth) {
            month.value = target
            // Keep the day panel inside the visible month: today when it is
            // this month, its first day otherwise.
            val today = clock.instant().atZone(clock.zone).toLocalDate()
            selectedDate.value = if (YearMonth.from(today) == target) null else target.atDay(1)
        }

        fun select(date: LocalDate) {
            selectedDate.value = date
        }

        /** Retroactive flip from the day sheet; the engine keeps stock and state consistent. */
        fun mark(
            dose: TodayDose,
            taken: Boolean,
        ) {
            viewModelScope.launch {
                engine.onUserAction(dose.key, if (taken) UserDoseAction.TAKE else UserDoseAction.SKIP)
            }
        }

        /** Revert an accidental mark; the dose becomes pending again. */
        fun undo(dose: TodayDose) {
            viewModelScope.launch { engine.undoDose(dose.key) }
        }

        /**
         * One-off dose for a specific day and time: a single-day FIXED_TIMES
         * schedule version, so the whole reminder/statistics pipeline treats
         * it like any other planned intake.
         */
        fun addOneOff(
            medicationId: String,
            date: LocalDate,
            time: LocalTime,
            amount: Double,
        ) {
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

        private fun gridStart(month: YearMonth): LocalDate {
            val first = month.atDay(1)
            val shift = (first.dayOfWeek.value + 6) % 7 // Monday-first.
            return first.minusDays(shift.toLong())
        }

        private companion object {
            const val GRID_DAYS = 42
        }
    }
