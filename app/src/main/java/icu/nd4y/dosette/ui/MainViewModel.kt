package icu.nd4y.dosette.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                AppSettings(),
            )
    }
