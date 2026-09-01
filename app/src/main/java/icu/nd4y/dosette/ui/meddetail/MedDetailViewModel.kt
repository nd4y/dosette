package icu.nd4y.dosette.ui.meddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.reminders.ReminderEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

data class MedDetailUiState(
    val loading: Boolean = true,
    val details: MedicationDetails? = null,
    /** Last 30 days of this medication's intake, oldest first. */
    val days: List<AdherenceDay> = emptyList(),
    val adherencePercent: Int? = null,
    /** The medication is gone — the screen must close. */
    val deleted: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MedDetailViewModel.Factory::class)
class MedDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val medicationId: String,
        private val medicationRepository: MedicationRepository,
        doseLogRepository: DoseLogRepository,
        private val engine: ReminderEngine,
        private val clock: Clock,
    ) : ViewModel() {
        private val deleted = MutableStateFlow(false)

        val uiState: StateFlow<MedDetailUiState> =
            medicationRepository
                .observeDetails(medicationId)
                .flatMapLatest { details ->
                    if (details == null) {
                        flowOf(MedDetailUiState(loading = false))
                    } else {
                        val to = clock.instant().atZone(clock.zone).toLocalDate()
                        val from = to.minusDays(ADHERENCE_DAYS - 1L)
                        doseLogRepository
                            .observeRange(details.medication.profileId, from, to)
                            .map { logs ->
                                MedDetailUiState(
                                    loading = false,
                                    details = details,
                                    days = adherenceDays(logs, medicationId, from, to),
                                    adherencePercent = adherencePercent(logs, medicationId),
                                )
                            }
                    }
                }.combine(deleted) { state, gone -> state.copy(deleted = gone) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedDetailUiState())

        /** Adds a bought package on top of the current stock. */
        fun refill(
            variantId: String,
            units: Double,
        ) {
            viewModelScope.launch { medicationRepository.refill(variantId, units, clock.instant()) }
        }

        /** Correction path: overwrite the counter with a recount. */
        fun setStock(
            variantId: String,
            units: Double,
        ) {
            viewModelScope.launch {
                medicationRepository.getVariant(variantId)?.let { variant ->
                    medicationRepository.upsertVariant(variant.copy(currentStock = units))
                }
            }
        }

        fun archive() {
            viewModelScope.launch {
                medicationRepository.archive(medicationId, clock.instant())
                // Drops the med's pending reminders and re-arms the alarm.
                engine.reschedule()
            }
        }

        fun unarchive() {
            viewModelScope.launch {
                medicationRepository.unarchive(medicationId)
                engine.reschedule()
            }
        }

        fun delete() {
            viewModelScope.launch {
                medicationRepository.delete(medicationId)
                engine.reschedule()
                deleted.value = true
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(medicationId: String): MedDetailViewModel
        }

        private companion object {
            const val ADHERENCE_DAYS = 30
        }
    }
