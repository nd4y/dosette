package icu.nd4y.dosette.ui.meddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = MedDetailViewModel.Factory::class)
class MedDetailViewModel
    @AssistedInject
    constructor(
        @Assisted medicationId: String,
        medicationRepository: MedicationRepository,
    ) : ViewModel() {
        val details: StateFlow<MedicationDetails?> =
            medicationRepository
                .observeDetails(medicationId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @AssistedFactory
        interface Factory {
            fun create(medicationId: String): MedDetailViewModel
        }
    }
