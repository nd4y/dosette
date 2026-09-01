package icu.nd4y.dosette.ui.mededit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.MedicationVariant
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleTime
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.ui.designsystem.MedPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

enum class WizardStep { BASICS, SCHEDULE, TIMES, STOCK, REVIEW }

data class TimeSlotDraft(
    val time: LocalTime,
    val amountText: String = "1",
)

data class VariantDraft(
    val id: String = UUID.randomUUID().toString(),
    val strengthText: String = "",
    val stockText: String = "",
    val thresholdText: String = "10",
    val refillText: String = "30",
)

data class MedEditUiState(
    val step: WizardStep = WizardStep.BASICS,
    /** True when the wizard edits an existing medication instead of creating one. */
    val editing: Boolean = false,
    // Basics.
    val name: String = "",
    val form: MedicationForm = MedicationForm.TABLET,
    val strengthText: String = "",
    val strengthUnit: String = "",
    val instructions: String = "",
    val colorSeed: Int = 0,
    // Schedule.
    val scheduleType: ScheduleType = ScheduleType.FIXED_TIMES,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val intervalText: String = "2",
    val cycleOnText: String = "21",
    val cycleOffText: String = "7",
    // Times.
    val times: List<TimeSlotDraft> = listOf(TimeSlotDraft(LocalTime.of(8, 0))),
    // Stock: package variants, first one is the default.
    val trackStock: Boolean = false,
    val variants: List<VariantDraft> = listOf(VariantDraft()),
    val saved: Boolean = false,
) {
    val stepIndex: Int get() = visibleSteps.indexOf(step)
    val stepCount: Int get() = visibleSteps.size

    /** PRN has no times to configure. */
    val visibleSteps: List<WizardStep>
        get() =
            if (scheduleType == ScheduleType.AS_NEEDED) {
                listOf(WizardStep.BASICS, WizardStep.SCHEDULE, WizardStep.STOCK, WizardStep.REVIEW)
            } else {
                WizardStep.entries.toList()
            }

    val canProceed: Boolean
        get() =
            when (step) {
                WizardStep.BASICS -> {
                    name.isNotBlank()
                }

                WizardStep.SCHEDULE -> {
                    when (scheduleType) {
                        ScheduleType.WEEKDAYS -> {
                            weekdays.isNotEmpty()
                        }

                        ScheduleType.EVERY_N_DAYS -> {
                            (intervalText.toIntOrNull() ?: 0) >= 1
                        }

                        ScheduleType.CYCLE -> {
                            (cycleOnText.toIntOrNull() ?: 0) >= 1 && (cycleOffText.toIntOrNull() ?: 0) >= 0
                        }

                        else -> {
                            true
                        }
                    }
                }

                WizardStep.TIMES -> {
                    times.isNotEmpty() && times.all { (it.amountText.toDoubleOrNull() ?: 0.0) > 0 }
                }

                WizardStep.STOCK -> {
                    !trackStock || variants.all { (it.stockText.toDoubleOrNull() ?: -1.0) >= 0.0 }
                }

                WizardStep.REVIEW -> {
                    true
                }
            }
}

@HiltViewModel(assistedFactory = MedEditViewModel.Factory::class)
class MedEditViewModel
    @AssistedInject
    constructor(
        @Assisted private val medicationId: String?,
        private val medicationRepository: MedicationRepository,
        private val settingsRepository: SettingsRepository,
        private val engine: ReminderEngine,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MedEditUiState(colorSeed = (0 until MedPalette.size).random()))
        val uiState: StateFlow<MedEditUiState> = _uiState.asStateFlow()

        /** The medication as loaded for editing; null in the create flow. */
        private var original: MedicationDetails? = null

        init {
            if (medicationId != null) {
                viewModelScope.launch {
                    medicationRepository.getDetails(medicationId)?.let { details ->
                        original = details
                        _uiState.value = prefillFrom(details)
                    }
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(medicationId: String?): MedEditViewModel
        }

        fun update(transform: (MedEditUiState) -> MedEditUiState) {
            _uiState.value = transform(_uiState.value)
        }

        fun next() {
            val state = _uiState.value
            if (!state.canProceed) return
            val steps = state.visibleSteps
            val index = steps.indexOf(state.step)
            if (index == steps.lastIndex) {
                save()
            } else {
                _uiState.value = state.copy(step = steps[index + 1])
            }
        }

        /** @return false when already on the first step (caller closes the wizard). */
        fun back(): Boolean {
            val state = _uiState.value
            val steps = state.visibleSteps
            val index = steps.indexOf(state.step)
            if (index == 0) return false
            _uiState.value = state.copy(step = steps[index - 1])
            return true
        }

        /** A double tap on Save must not create the medication twice. */
        private var saveInFlight = false

        private fun save() {
            if (saveInFlight) return
            saveInFlight = true
            val state = _uiState.value
            val editTarget = original
            if (state.editing && editTarget != null) {
                viewModelScope.launch {
                    saveEdit(state, editTarget)
                    engine.reschedule()
                    _uiState.value = state.copy(saved = true)
                }
                return
            }
            viewModelScope.launch {
                val profileId =
                    settingsRepository.settings.first().activeProfileId
                        ?: run {
                            saveInFlight = false
                            return@launch
                        }
                val now = clock.instant()
                val medicationId = UUID.randomUUID().toString()

                val strengthValue = state.strengthText.replace(',', '.').toDoubleOrNull()
                val variants = buildVariants(state, medicationId, strengthValue)

                medicationRepository.upsert(
                    Medication(
                        id = medicationId,
                        profileId = profileId,
                        name = state.name.trim(),
                        form = state.form,
                        strengthValue = strengthValue,
                        strengthUnit = state.strengthUnit.trim().ifEmpty { null },
                        instructions = state.instructions.trim().ifEmpty { null },
                        colorSeed = state.colorSeed,
                        iconKey = state.form.name.lowercase(),
                        defaultVariantId = variants.firstOrNull()?.id,
                        archivedAt = null,
                        createdAt = now,
                    ),
                )
                variants.forEach { medicationRepository.upsertVariant(it) }
                medicationRepository.addSchedule(buildSchedule(state, medicationId, now))

                engine.reschedule()
                _uiState.value = state.copy(saved = true)
            }
        }

        /**
         * Edit flow: the medication row is updated in place, variants keep
         * their ids (stock history survives), and a changed intake plan
         * becomes a new schedule version so past days recompute identically.
         */
        private suspend fun saveEdit(
            state: MedEditUiState,
            original: MedicationDetails,
        ) {
            val now = clock.instant()
            val today = now.atZone(clock.zone).toLocalDate()
            val med = original.medication
            val strengthValue = state.strengthText.replace(',', '.').toDoubleOrNull()
            val variants = buildVariantsForEdit(state, original, strengthValue)

            medicationRepository.upsert(
                med.copy(
                    name = state.name.trim(),
                    form = state.form,
                    strengthValue = strengthValue,
                    strengthUnit = state.strengthUnit.trim().ifEmpty { null },
                    instructions = state.instructions.trim().ifEmpty { null },
                    colorSeed = state.colorSeed,
                    iconKey = state.form.name.lowercase(),
                    defaultVariantId = variants.firstOrNull()?.id,
                ),
            )
            val keptIds = variants.map { it.id }.toSet()
            original.variants.filter { it.id !in keptIds }.forEach {
                medicationRepository.deleteVariant(it.id)
            }
            variants.forEach { medicationRepository.upsertVariant(it) }

            val current = mainScheduleOf(original.schedules)
            val replacement = buildSchedule(state, med.id, now)
            when {
                current == null -> {
                    medicationRepository.addSchedule(replacement)
                }

                scheduleMatches(current, replacement) -> {
                    Unit
                }

                current.startDate.isBefore(today) -> {
                    medicationRepository.replaceSchedule(current.id, today.minusDays(1), replacement)
                }

                else -> {
                    // The current version starts today: closing it yesterday
                    // would invert its date range — swap it out instead.
                    medicationRepository.deleteSchedule(current.id)
                    medicationRepository.addSchedule(replacement)
                }
            }
        }

        private fun buildVariantsForEdit(
            state: MedEditUiState,
            original: MedicationDetails,
            medicationStrength: Double?,
        ): List<MedicationVariant> {
            val med = original.medication
            val unit = state.strengthUnit.trim().ifEmpty { null }
            if (!state.trackStock) {
                // Keep one variant (reusing an existing row so its stock
                // figures survive a track-off/track-on round trip).
                val keep = original.defaultVariant
                return listOf(
                    MedicationVariant(
                        id = keep?.id ?: UUID.randomUUID().toString(),
                        medicationId = med.id,
                        label = null,
                        strengthValue = medicationStrength,
                        strengthUnit = unit,
                        sortOrder = 0,
                        trackingEnabled = false,
                        currentStock = keep?.currentStock ?: 0.0,
                        lowStockThreshold = keep?.lowStockThreshold,
                        defaultRefillAmount = keep?.defaultRefillAmount,
                        lastRefillAt = keep?.lastRefillAt,
                    ),
                )
            }
            val priorById = original.variants.associateBy { it.id }
            return state.variants.mapIndexed { index, draft ->
                val prior = priorById[draft.id]
                MedicationVariant(
                    id = draft.id,
                    medicationId = med.id,
                    label = null,
                    strengthValue =
                        draft.strengthText.replace(',', '.').toDoubleOrNull() ?: medicationStrength,
                    strengthUnit = unit,
                    sortOrder = index,
                    trackingEnabled = true,
                    currentStock = draft.stockText.replace(',', '.').toDoubleOrNull() ?: prior?.currentStock ?: 0.0,
                    lowStockThreshold = draft.thresholdText.replace(',', '.').toDoubleOrNull(),
                    defaultRefillAmount = draft.refillText.replace(',', '.').toDoubleOrNull(),
                    lastRefillAt = prior?.lastRefillAt,
                )
            }
        }

        private fun buildVariants(
            state: MedEditUiState,
            medicationId: String,
            medicationStrength: Double?,
        ): List<MedicationVariant> {
            if (!state.trackStock) {
                // One untracked variant so Take always has a place to point at.
                return listOf(
                    MedicationVariant(
                        id = UUID.randomUUID().toString(),
                        medicationId = medicationId,
                        label = null,
                        strengthValue = medicationStrength,
                        strengthUnit = state.strengthUnit.trim().ifEmpty { null },
                        sortOrder = 0,
                        trackingEnabled = false,
                        currentStock = 0.0,
                        lowStockThreshold = null,
                        defaultRefillAmount = null,
                        lastRefillAt = null,
                    ),
                )
            }
            return state.variants.mapIndexed { index, draft ->
                MedicationVariant(
                    id = draft.id,
                    medicationId = medicationId,
                    label = null,
                    strengthValue = draft.strengthText.replace(',', '.').toDoubleOrNull() ?: medicationStrength,
                    strengthUnit = state.strengthUnit.trim().ifEmpty { null },
                    sortOrder = index,
                    trackingEnabled = true,
                    currentStock = draft.stockText.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    lowStockThreshold = draft.thresholdText.replace(',', '.').toDoubleOrNull(),
                    defaultRefillAmount = draft.refillText.replace(',', '.').toDoubleOrNull(),
                    lastRefillAt = null,
                )
            }
        }

        private fun buildSchedule(
            state: MedEditUiState,
            medicationId: String,
            now: java.time.Instant,
        ): Schedule {
            val scheduleId = UUID.randomUUID().toString()
            val today = now.atZone(clock.zone).toLocalDate()
            val times =
                if (state.scheduleType == ScheduleType.AS_NEEDED) {
                    emptyList()
                } else {
                    // Two slots at the same wall-clock minute would collapse
                    // into one occurrence identity anyway — keep the first.
                    state.times
                        .sortedBy { it.time }
                        .distinctBy { it.time }
                        .mapIndexed { index, slot ->
                            ScheduleTime(
                                id = UUID.randomUUID().toString(),
                                scheduleId = scheduleId,
                                time = slot.time,
                                doseAmount = slot.amountText.replace(',', '.').toDoubleOrNull() ?: 1.0,
                                sortIndex = index,
                            )
                        }
                }
            return Schedule(
                id = scheduleId,
                medicationId = medicationId,
                type = state.scheduleType,
                startDate = today,
                endDate = null,
                weekdays = if (state.scheduleType == ScheduleType.WEEKDAYS) state.weekdays else emptySet(),
                intervalDays =
                    if (state.scheduleType == ScheduleType.EVERY_N_DAYS) intervalOrNull(state) else null,
                cycleDaysOn = if (state.scheduleType == ScheduleType.CYCLE) state.cycleOnText.toIntOrNull() else null,
                cycleDaysOff = if (state.scheduleType == ScheduleType.CYCLE) state.cycleOffText.toIntOrNull() else null,
                defaultDoseAmount = times.firstOrNull()?.doseAmount ?: 1.0,
                remindersEnabled = state.scheduleType != ScheduleType.AS_NEEDED,
                createdAt = now,
                times = times,
            )
        }

        private fun intervalOrNull(state: MedEditUiState): Int? = state.intervalText.toIntOrNull()
    }
