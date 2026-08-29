package icu.nd4y.dosette.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** null until DataStore emits — prevents an onboarding flash for existing users. */
        val settings: StateFlow<AppSettings?> =
            settingsRepository.settings
                .map<AppSettings, AppSettings?> { it }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    null,
                )
    }
