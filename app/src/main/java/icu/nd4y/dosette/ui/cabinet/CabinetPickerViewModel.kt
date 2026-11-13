package icu.nd4y.dosette.ui.cabinet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.CabinetRepository
import icu.nd4y.dosette.data.repository.CabinetState
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.reminders.ReminderEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CabinetPickerViewModel
    @Inject
    constructor(
        private val repository: CabinetRepository,
        settings: SettingsRepository,
        private val engine: ReminderEngine,
    ) : ViewModel() {
        private val requestedProfile = MutableStateFlow<String?>(null)
        private val profile =
            combine(requestedProfile, settings.settings) { requested, current ->
                requested ?: current.activeProfileId
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        val state =
            profile
                .flatMapLatest { id -> id?.let(repository::observe) ?: flowOf(CabinetState()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CabinetState())
        val error = MutableStateFlow(false)

        fun saveName(
            id: String?,
            name: String,
        ) {
            if (id == null) create(name) else rename(id, name)
        }

        fun bind(profileId: String?) {
            requestedProfile.value = profileId
        }

        fun dismissError() {
            error.value = false
        }

        fun select(id: String) = change { repository.select(it, id) }

        fun create(name: String) =
            change {
                val cabinet = repository.create(it, name)
                repository.select(it, cabinet)
            }

        fun rename(
            id: String,
            name: String,
        ) = change { repository.rename(it, id, name) }

        fun archive(id: String) = change { repository.archive(it, id) }

        fun restore(id: String) = change { repository.restore(it, id) }

        private fun change(action: suspend (String) -> Unit) {
            val profileId = profile.value ?: return
            viewModelScope.launch {
                try {
                    action(profileId)
                    engine.reschedule()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: IllegalArgumentException) {
                    error.value = true
                }
            }
        }
    }
