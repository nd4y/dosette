package icu.nd4y.dosette.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.ProfileRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.WidgetRefresher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
)

@HiltViewModel
class ProfilesViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val settingsRepository: SettingsRepository,
        private val engine: ReminderEngine,
        private val widgetRefresher: WidgetRefresher,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<ProfilesUiState> =
            combine(profileRepository.observeAll(), settingsRepository.settings) { profiles, settings ->
                ProfilesUiState(profiles = profiles, activeProfileId = settings.activeProfileId)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

        fun setActive(id: String) {
            viewModelScope.launch {
                settingsRepository.setActiveProfileId(id)
                // The widget follows the active profile; Glance sessions
                // expire, so it needs an explicit nudge.
                widgetRefresher.refresh()
            }
        }

        fun save(
            id: String?,
            name: String,
            colorSeed: Int,
        ) {
            if (name.isBlank()) return
            viewModelScope.launch {
                val existing = id?.let { current -> uiState.value.profiles.firstOrNull { it.id == current } }
                profileRepository.upsert(
                    existing?.copy(name = name.trim(), colorSeed = colorSeed)
                        ?: Profile(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            colorSeed = colorSeed,
                            avatarKey = null,
                            sortOrder = uiState.value.profiles.size,
                            createdAt = clock.instant(),
                        ),
                )
            }
        }

        fun delete(id: String) {
            viewModelScope.launch {
                val remaining = uiState.value.profiles.filter { it.id != id }
                if (remaining.isEmpty()) return@launch
                profileRepository.delete(id)
                if (uiState.value.activeProfileId == id) {
                    settingsRepository.setActiveProfileId(remaining.first().id)
                }
                engine.reschedule()
            }
        }
    }
