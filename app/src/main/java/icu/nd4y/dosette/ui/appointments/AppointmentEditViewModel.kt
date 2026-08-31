package icu.nd4y.dosette.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.AppointmentRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.reminders.ReminderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class AppointmentDraft(
    val loaded: Boolean = false,
    val editingExisting: Boolean = false,
    val title: String = "",
    val doctor: String = "",
    val location: String = "",
    /** Placeholder only — the ViewModel always seeds a real date before showing the form. */
    val date: LocalDate = LocalDate.of(1970, 1, 1),
    val time: LocalTime = LocalTime.of(10, 0),
    val notes: String = "",
    /** Reminder offsets in minutes before the visit. */
    val offsets: Set<Int> = setOf(DEFAULT_OFFSET_MIN),
) {
    val valid: Boolean get() = title.isNotBlank()

    companion object {
        const val DEFAULT_OFFSET_MIN = 120
    }
}

@HiltViewModel(assistedFactory = AppointmentEditViewModel.Factory::class)
class AppointmentEditViewModel
    @AssistedInject
    constructor(
        @Assisted private val appointmentId: String?,
        private val appointmentRepository: AppointmentRepository,
        private val settingsRepository: SettingsRepository,
        private val engine: ReminderEngine,
        private val clock: Clock,
    ) : ViewModel() {
        private val _draft = MutableStateFlow(AppointmentDraft(date = LocalDate.now(clock).plusDays(1)))
        val draft: StateFlow<AppointmentDraft> = _draft

        private var existing: Appointment? = null

        init {
            viewModelScope.launch {
                existing = appointmentId?.let { appointmentRepository.getById(it) }
                _draft.update { draft ->
                    existing?.let { appointment ->
                        AppointmentDraft(
                            loaded = true,
                            editingExisting = true,
                            title = appointment.title,
                            doctor = appointment.doctorName.orEmpty(),
                            location = appointment.location.orEmpty(),
                            date = appointment.date,
                            time = appointment.time,
                            notes = appointment.notes.orEmpty(),
                            offsets = appointment.reminderOffsetsMin.toSet(),
                        )
                    } ?: draft.copy(loaded = true)
                }
            }
        }

        fun update(transform: (AppointmentDraft) -> AppointmentDraft) {
            _draft.update(transform)
        }

        /** A double tap on Save must not create the appointment twice. */
        private var saveInFlight = false

        fun save(onDone: () -> Unit) {
            val draft = _draft.value
            if (!draft.valid || saveInFlight) return
            saveInFlight = true
            viewModelScope.launch {
                val profileId =
                    existing?.profileId
                        ?: settingsRepository.settings
                            .first()
                            .activeProfileId
                        ?: run {
                            saveInFlight = false
                            return@launch
                        }
                appointmentRepository.upsert(
                    Appointment(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        profileId = profileId,
                        title = draft.title.trim(),
                        doctorName = draft.doctor.trim().ifEmpty { null },
                        location = draft.location.trim().ifEmpty { null },
                        date = draft.date,
                        time = draft.time,
                        notes = draft.notes.trim().ifEmpty { null },
                        reminderOffsetsMin = draft.offsets.sortedDescending(),
                        createdAt = existing?.createdAt ?: clock.instant(),
                    ),
                )
                engine.reschedule()
                onDone()
            }
        }

        fun delete(onDone: () -> Unit) {
            val id = existing?.id ?: return
            viewModelScope.launch {
                appointmentRepository.delete(id)
                engine.reschedule()
                onDone()
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(appointmentId: String?): AppointmentEditViewModel
        }
    }
