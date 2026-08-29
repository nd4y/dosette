package icu.nd4y.dosette.ui.cabinet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.inventory.InventoryPolicy
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

/** Structured schedule summary; the UI turns it into localized text. */
sealed interface ScheduleBrief {
    data class FixedTimes(
        val times: List<LocalTime>,
    ) : ScheduleBrief

    data class Weekdays(
        val days: Int,
        val times: List<LocalTime>,
    ) : ScheduleBrief

    data class EveryNDays(
        val interval: Int,
        val times: List<LocalTime>,
    ) : ScheduleBrief

    data class Cycle(
        val daysOn: Int,
        val daysOff: Int,
    ) : ScheduleBrief

    data object AsNeeded : ScheduleBrief

    data object None : ScheduleBrief
}

data class MedCard(
    val id: String,
    val name: String,
    val strengthText: String?,
    val form: MedicationForm,
    val colorSeed: Int,
    val schedule: ScheduleBrief,
    /** Units left in the default variant; null = stock not tracked. */
    val stockUnits: String?,
    val daysOfSupply: Int?,
    val lowStock: Boolean,
)

data class CabinetUiState(
    val loading: Boolean = true,
    val active: List<MedCard> = emptyList(),
    val archived: List<MedCard> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CabinetViewModel
    @Inject
    constructor(
        medicationRepository: MedicationRepository,
        settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<CabinetUiState> =
            settingsRepository.settings
                .map { it.activeProfileId }
                .distinctUntilChanged()
                .flatMapLatest { profileId ->
                    if (profileId == null) {
                        flowOf(emptyList())
                    } else {
                        medicationRepository.observeByProfile(profileId)
                    }
                }.map { meds ->
                    CabinetUiState(
                        loading = false,
                        active = meds.filter { !it.medication.isArchived }.map { it.toCard() },
                        archived = meds.filter { it.medication.isArchived }.map { it.toCard() },
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CabinetUiState())

        private fun MedicationDetails.toCard(): MedCard {
            val today = clock.instant().atZone(clock.zone).toLocalDate()
            val activeSchedules = schedulesActiveOn(today)
            val variant = defaultVariant
            val trackedStock = variant?.takeIf { it.trackingEnabled }

            val dailyDoseUnits = InventoryPolicy.dailyConsumption(activeSchedules)
            val dailyVariantUnits =
                trackedStock?.let {
                    InventoryPolicy.unitsForDose(dailyDoseUnits, medication.strengthValue, it.strengthValue)
                } ?: 0.0
            val daysOfSupply =
                trackedStock?.let { InventoryPolicy.daysOfSupply(it.currentStock, dailyVariantUnits) }

            return MedCard(
                id = medication.id,
                name = medication.name,
                strengthText =
                    medication.strengthValue?.let {
                        "${formatAmount(it)} ${medication.strengthUnit.orEmpty()}".trim()
                    },
                form = medication.form,
                colorSeed = medication.colorSeed,
                schedule = activeSchedules.toBrief(),
                stockUnits = trackedStock?.let { formatAmount(it.currentStock) },
                daysOfSupply = daysOfSupply,
                lowStock =
                    trackedStock?.lowStockThreshold?.let { trackedStock.currentStock <= it } == true,
            )
        }

        private fun List<Schedule>.toBrief(): ScheduleBrief {
            val schedule = firstOrNull() ?: return ScheduleBrief.None
            val times = schedule.times.map { it.time }
            return when (schedule.type) {
                ScheduleType.FIXED_TIMES -> {
                    ScheduleBrief.FixedTimes(times)
                }

                ScheduleType.WEEKDAYS -> {
                    ScheduleBrief.Weekdays(schedule.weekdays.size, times)
                }

                ScheduleType.EVERY_N_DAYS -> {
                    ScheduleBrief.EveryNDays(schedule.intervalDays ?: 1, times)
                }

                ScheduleType.CYCLE -> {
                    ScheduleBrief.Cycle(schedule.cycleDaysOn ?: 0, schedule.cycleDaysOff ?: 0)
                }

                ScheduleType.AS_NEEDED -> {
                    ScheduleBrief.AsNeeded
                }
            }
        }
    }

fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
