package icu.nd4y.dosette.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.AppointmentRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.ui.common.dayTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

data class AppointmentsUiState(
    val loading: Boolean = true,
    val upcoming: List<Appointment> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppointmentsViewModel
    @Inject
    constructor(
        appointmentRepository: AppointmentRepository,
        settingsRepository: SettingsRepository,
        clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<AppointmentsUiState> =
            combine(
                settingsRepository.settings.map { it.activeProfileId }.distinctUntilChanged(),
                // "Upcoming" is relative to today: yesterday's visits must
                // drop off after midnight, not on the next data change.
                dayTicker(clock),
            ) { profileId, today -> profileId to today }
                .flatMapLatest { (profileId, today) ->
                    if (profileId == null) {
                        flowOf(AppointmentsUiState(loading = false))
                    } else {
                        appointmentRepository
                            .observeUpcoming(profileId, today)
                            .map { AppointmentsUiState(loading = false, upcoming = it) }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppointmentsUiState())
    }
