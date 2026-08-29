package icu.nd4y.dosette.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.reminders.ReminderEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val engine: ReminderEngine,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                AppSettings(),
            )

        fun setNagInterval(value: Int) {
            viewModelScope.launch {
                settingsRepository.setNagIntervalMin(value)
                engine.reschedule()
            }
        }

        fun setSnooze(value: Int) {
            viewModelScope.launch { settingsRepository.setSnoozeMin(value) }
        }

        fun setGrace(value: Int) {
            viewModelScope.launch {
                settingsRepository.setMissedGraceMin(value)
                engine.reschedule()
            }
        }

        fun setTheme(value: ThemeMode) {
            viewModelScope.launch { settingsRepository.setTheme(value) }
        }

        fun setDynamicColor(value: Boolean) {
            viewModelScope.launch { settingsRepository.setDynamicColor(value) }
        }

        fun setLanguage(value: AppLanguage) {
            viewModelScope.launch { settingsRepository.setLanguage(value) }
            AppCompatDelegate.setApplicationLocales(
                when (value) {
                    AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                    AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
                    AppLanguage.RU -> LocaleListCompat.forLanguageTags("ru")
                },
            )
        }
    }
