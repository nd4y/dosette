package icu.nd4y.dosette.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.inventory.InventoryPolicy
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.UserDoseAction
import icu.nd4y.dosette.ui.cabinet.formatAmount
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val doses: List<TodayDose> = emptyList(),
    val prn: List<PrnMed> = emptyList(),
    val takenCount: Int = 0,
    val plannedCount: Int = 0,
    val nextDoseTime: LocalTime? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val doseLogRepository: DoseLogRepository,
        settingsRepository: SettingsRepository,
        private val engine: ReminderEngine,
        private val clock: Clock,
    ) : ViewModel() {
        private val today: LocalDate get() = clock.instant().atZone(clock.zone).toLocalDate()

        val uiState: StateFlow<TodayUiState> =
            settingsRepository.settings
                .map { it.activeProfileId }
                .distinctUntilChanged()
                .flatMapLatest { profileId ->
                    if (profileId == null) {
                        flowOf(TodayUiState(loading = false, date = today))
                    } else {
                        val date = today
                        combine(
                            medicationRepository.observeByProfile(profileId),
                            doseLogRepository.observeRange(profileId, date, date),
                        ) { meds, logs -> buildState(date, meds, logs) }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

        private fun buildState(
            date: LocalDate,
            meds: List<MedicationDetails>,
            logs: List<DoseLog>,
        ): TodayUiState {
            val active = meds.filter { !it.medication.isArchived }
            val doses = buildDayDoses(date, meds, logs, clock.zone)

            val prn =
                active
                    .filter { med -> med.schedules.any { it.endDate == null && it.type == ScheduleType.AS_NEEDED } }
                    .map { med ->
                        PrnMed(
                            medicationId = med.medication.id,
                            name = med.medication.name,
                            strengthText =
                                med.medication.strengthValue?.let {
                                    "${formatAmount(it)} ${med.medication.strengthUnit.orEmpty()}".trim()
                                },
                            form = med.medication.form,
                            colorSeed = med.medication.colorSeed,
                        )
                    }

            val now = clock.instant().atZone(clock.zone).toLocalTime()
            return TodayUiState(
                loading = false,
                date = date,
                doses = doses,
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

        fun takePrn(prnMed: PrnMed) {
            viewModelScope.launch {
                val med = medicationRepository.getDetails(prnMed.medicationId) ?: return@launch
                val schedule =
                    med.schedules.firstOrNull { it.endDate == null && it.type == ScheduleType.AS_NEEDED }
                        ?: return@launch
                val variant = med.defaultVariant
                val amount = schedule.defaultDoseAmount
                val consumed =
                    variant?.let {
                        InventoryPolicy.unitsForDose(amount, med.medication.strengthValue, it.strengthValue)
                    }
                val now = clock.instant()
                doseLogRepository.recordPrn(
                    DoseLog(
                        id = UUID.randomUUID().toString(),
                        profileId = med.medication.profileId,
                        medicationId = med.medication.id,
                        scheduleId = schedule.id,
                        kind = DoseKind.PRN,
                        date = now.atZone(clock.zone).toLocalDate(),
                        time = null,
                        scheduledAt = null,
                        status = DoseStatus.TAKEN,
                        actedAt = now,
                        amount = amount,
                        variantId = variant?.id,
                        consumedUnits = consumed,
                        note = null,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
