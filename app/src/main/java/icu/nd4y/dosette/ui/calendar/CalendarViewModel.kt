package icu.nd4y.dosette.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.stats.AdherenceCalculator
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.UserDoseAction
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
import java.time.YearMonth
import javax.inject.Inject

data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    /** null = nothing recorded (future or empty day). */
    val status: AdherenceCalculator.DayStatus?,
)

data class CalendarUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    /** Always full weeks, Monday-first. */
    val days: List<CalendarDay> = emptyList(),
    val monthAdherencePercent: Int? = null,
    val selectedDate: LocalDate? = null,
    val selectedDoses: List<TodayDose> = emptyList(),
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
                            ) { meds, logs, selected ->
                                val today = clock.instant().atZone(clock.zone).toLocalDate()
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
                                    selectedDoses =
                                        selected?.let { buildDayDoses(it, meds, logs, clock.zone) }.orEmpty(),
                                )
                            }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

        fun previousMonth() {
            month.value = month.value.minusMonths(1)
        }

        fun nextMonth() {
            month.value = month.value.plusMonths(1)
        }

        fun showMonth(target: YearMonth) {
            month.value = target
        }

        fun select(date: LocalDate?) {
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

        private fun gridStart(month: YearMonth): LocalDate {
            val first = month.atDay(1)
            val shift = (first.dayOfWeek.value + 6) % 7 // Monday-first.
            return first.minusDays(shift.toLong())
        }

        private companion object {
            const val GRID_DAYS = 42
        }
    }
