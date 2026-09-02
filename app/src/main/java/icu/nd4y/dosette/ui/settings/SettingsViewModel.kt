package icu.nd4y.dosette.ui.settings

import android.content.Context
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.ui.common.applyAppLanguage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val engine: ReminderEngine,
        @param:ApplicationContext private val context: Context,
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

        fun setAlarmClock(value: Boolean) {
            viewModelScope.launch {
                settingsRepository.setAlarmClock(value)
                // Re-arms the pending alarm with the new flavour right away.
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
            applyAppLanguage(value)
        }

        /** Geo of [id] from the current device position; keeps any bound Wi-Fi. */
        fun setPlaceFromLocation(
            id: PlaceId,
            latitude: Double,
            longitude: Double,
        ) {
            viewModelScope.launch {
                val current = settingsRepository.settings.first().places[id] ?: PlaceConfig()
                settingsRepository.setPlace(id, current.copy(latitude = latitude, longitude = longitude))
            }
        }

        /** Binds the currently connected Wi-Fi SSID to [id]; no-op when unknown. */
        fun bindCurrentWifi(id: PlaceId) {
            @Suppress("DEPRECATION")
            val ssid =
                context
                    .getSystemService(WifiManager::class.java)
                    ?.connectionInfo
                    ?.ssid
                    ?.trim('"')
                    ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
                    ?: return
            viewModelScope.launch {
                val current = settingsRepository.settings.first().places[id] ?: PlaceConfig()
                settingsRepository.setPlace(id, current.copy(wifiSsid = ssid))
            }
        }

        fun clearPlace(id: PlaceId) {
            viewModelScope.launch {
                settingsRepository.setPlace(id, null)
                engine.reschedule()
            }
        }
    }
