package icu.nd4y.dosette.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.repository.ProfileRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.domain.nag.SnoozeTarget
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator
import icu.nd4y.dosette.reminders.PrnIntakes
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.UserDoseAction
import icu.nd4y.dosette.reminders.WidgetRefresher
import icu.nd4y.dosette.ui.common.dayTicker
import icu.nd4y.dosette.ui.common.strengthLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

/** One day of the continuous timeline the Today screen scrolls through. */
data class TimelineDay(
    val date: LocalDate,
    val doses: List<TodayDose>,
)

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    /**
     * The scrollable window around today, past first. Empty days are
     * dropped except today itself, which is always present.
     */
    val days: List<TimelineDay> = emptyList(),
    /**
     * Where the timeline should land when the screen opens: today, or
     * the earliest past day that still has an unresolved dose (a snooze
     * that crossed midnight must be seen, not scrolled for).
     */
    val anchorDate: LocalDate = date,
    val prn: List<PrnMed> = emptyList(),
    val takenCount: Int = 0,
    val plannedCount: Int = 0,
    val nextDoseTime: LocalTime? = null,
    /** Places configured well enough to snooze until. */
    val snoozePlaces: Set<PlaceId> = emptySet(),
    /** Shown as switcher chips only when more than one exists. */
    val profiles: List<ProfileChip> = emptyList(),
    val activeProfileId: String? = null,
) {
    val todayDoses: List<TodayDose> get() = days.firstOrNull { it.date == date }?.doses.orEmpty()
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

        val uiState: StateFlow<TodayUiState> =
            combine(
                settingsRepository.settings,
                dayTicker(clock),
            ) { settings, date -> Triple(settings.activeProfileId, settings.places, date) }
                .distinctUntilChanged()
                .flatMapLatest { (profileId, places, date) ->
                    val snoozePlaces = places.filterValues { it.isConfigured }.keys
                    if (profileId == null) {
                        flowOf(TodayUiState(loading = false, date = date))
                    } else {
                        combine(
                            medicationRepository.observeByProfile(profileId),
                            doseLogRepository.observeRange(
                                profileId,
                                date.minusDays(PAST_DAYS),
                                date.plusDays(FUTURE_DAYS),
                            ),
                            profileRepository.observeAll(),
                        ) { meds, logs, profiles ->
                            buildState(date, meds, logs).copy(
                                snoozePlaces = snoozePlaces,
                                profiles = profiles.map { ProfileChip(it.id, it.name, it.colorSeed) },
                                activeProfileId = profileId,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

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

        private fun buildState(
            date: LocalDate,
            meds: List<MedicationDetails>,
            logs: List<DoseLog>,
        ): TodayUiState {
            val active = meds.filter { !it.medication.isArchived }
            val days =
                (-PAST_DAYS..FUTURE_DAYS).mapNotNull { offset ->
                    val day = date.plusDays(offset)
                    val dayDoses = buildDayDoses(day, meds, logs, clock.zone)
                    if (dayDoses.isEmpty() && day != date) null else TimelineDay(day, dayDoses)
                }
            val doses = days.first { it.date == date }.doses
            val anchorDate =
                days
                    .firstOrNull { day -> day.date < date && day.doses.any { it.status == DoseUiStatus.PENDING } }
                    ?.date ?: date

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
                date = date,
                days = days,
                anchorDate = anchorDate,
                prn = prn,
                takenCount = doses.count { it.status == DoseUiStatus.TAKEN },
                plannedCount = doses.size,
                nextDoseTime =
                    doses
                        .filter { it.status == DoseUiStatus.PENDING && it.time >= now }
                        .minOfOrNull { it.time },
            )
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

        companion object {
            /**
             * The agenda window around today. One day back is enough: the
             * only unresolved past doses the engine keeps alive are the ones
             * from the night before, and deeper history lives in Calendar.
             */
            const val PAST_DAYS = 1L
            const val FUTURE_DAYS = 7L
        }
    }
