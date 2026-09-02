package icu.nd4y.dosette.testing

import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory [SettingsRepository]: every setter really lands in [state], so tests can assert on it. */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> get() = state

    override suspend fun setActiveProfileId(id: String?) {
        state.update { it.copy(activeProfileId = id) }
    }

    override suspend fun setNagIntervalMin(value: Int) {
        state.update { it.copy(nagIntervalMin = value) }
    }

    override suspend fun setNagMaxCount(value: Int) {
        state.update { it.copy(nagMaxCount = value) }
    }

    override suspend fun setSnoozeMin(value: Int) {
        state.update { it.copy(snoozeMin = value) }
    }

    override suspend fun setMissedGraceMin(value: Int) {
        state.update { it.copy(missedGraceMin = value) }
    }

    override suspend fun setTheme(value: ThemeMode) {
        state.update { it.copy(theme = value) }
    }

    override suspend fun setDynamicColor(value: Boolean) {
        state.update { it.copy(dynamicColor = value) }
    }

    override suspend fun setLanguage(value: AppLanguage) {
        state.update { it.copy(language = value) }
    }

    override suspend fun setLowStockNotifyEnabled(value: Boolean) {
        state.update { it.copy(lowStockNotifyEnabled = value) }
    }

    override suspend fun setAlarmClock(value: Boolean) {
        state.update { it.copy(alarmClock = value) }
    }

    override suspend fun setOnboardingDone(value: Boolean) {
        state.update { it.copy(onboardingDone = value) }
    }

    override suspend fun setLastAutoBackupAt(value: Instant?) {
        state.update { it.copy(lastAutoBackupAt = value) }
    }

    override suspend fun setLastAppointmentSweepAt(value: Instant) {
        state.update { it.copy(lastAppointmentSweepAt = value) }
    }

    override suspend fun setPlace(
        id: PlaceId,
        config: PlaceConfig?,
    ) {
        state.update { current ->
            current.copy(places = if (config == null) current.places - id else current.places + (id to config))
        }
    }

    /** Mirrors the real store: the backup schema carries no places or bookkeeping stamps, so those survive. */
    override suspend fun replaceAll(settings: AppSettings) {
        state.update { current ->
            settings.copy(
                places = current.places,
                lastAutoBackupAt = current.lastAutoBackupAt,
                lastAppointmentSweepAt = current.lastAppointmentSweepAt,
            )
        }
    }
}
