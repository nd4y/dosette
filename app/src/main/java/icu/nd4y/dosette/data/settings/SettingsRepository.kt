package icu.nd4y.dosette.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { SYSTEM, EN, RU }

data class AppSettings(
    val activeProfileId: String? = null,
    /** 0 = nag repeat off. */
    val nagIntervalMin: Int = 10,
    val nagMaxCount: Int = 6,
    val snoozeMin: Int = 10,
    val missedGraceMin: Int = 60,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val lowStockNotifyEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    val lastAutoBackupAt: Instant? = null,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setActiveProfileId(id: String?)

    suspend fun setNagIntervalMin(value: Int)

    suspend fun setNagMaxCount(value: Int)

    suspend fun setSnoozeMin(value: Int)

    suspend fun setMissedGraceMin(value: Int)

    suspend fun setTheme(value: ThemeMode)

    suspend fun setDynamicColor(value: Boolean)

    suspend fun setLanguage(value: AppLanguage)

    suspend fun setLowStockNotifyEnabled(value: Boolean)

    suspend fun setOnboardingDone(value: Boolean)

    suspend fun setLastAutoBackupAt(value: Instant?)

    /** Backup import: replaces everything atomically. */
    suspend fun replaceAll(settings: AppSettings)
}

@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        private object Keys {
            val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
            val NAG_INTERVAL_MIN = intPreferencesKey("nag_interval_min")
            val NAG_MAX_COUNT = intPreferencesKey("nag_max_count")
            val SNOOZE_MIN = intPreferencesKey("snooze_min")
            val MISSED_GRACE_MIN = intPreferencesKey("missed_grace_min")
            val THEME = stringPreferencesKey("theme")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val LANGUAGE = stringPreferencesKey("language")
            val LOW_STOCK_NOTIFY = booleanPreferencesKey("low_stock_notify")
            val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
            val LAST_AUTO_BACKUP_AT = longPreferencesKey("last_auto_backup_at")
        }

        private val defaults = AppSettings()

        override val settings: Flow<AppSettings> =
            dataStore.data.map { p ->
                AppSettings(
                    activeProfileId = p[Keys.ACTIVE_PROFILE_ID],
                    nagIntervalMin = p[Keys.NAG_INTERVAL_MIN] ?: defaults.nagIntervalMin,
                    nagMaxCount = p[Keys.NAG_MAX_COUNT] ?: defaults.nagMaxCount,
                    snoozeMin = p[Keys.SNOOZE_MIN] ?: defaults.snoozeMin,
                    missedGraceMin = p[Keys.MISSED_GRACE_MIN] ?: defaults.missedGraceMin,
                    theme = p[Keys.THEME]?.let(ThemeMode::valueOf) ?: defaults.theme,
                    dynamicColor = p[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
                    language = p[Keys.LANGUAGE]?.let(AppLanguage::valueOf) ?: defaults.language,
                    lowStockNotifyEnabled = p[Keys.LOW_STOCK_NOTIFY] ?: defaults.lowStockNotifyEnabled,
                    onboardingDone = p[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
                    lastAutoBackupAt = p[Keys.LAST_AUTO_BACKUP_AT]?.let(Instant::ofEpochMilli),
                )
            }

        override suspend fun setActiveProfileId(id: String?) {
            dataStore.edit { p ->
                if (id == null) p.remove(Keys.ACTIVE_PROFILE_ID) else p[Keys.ACTIVE_PROFILE_ID] = id
            }
        }

        override suspend fun setNagIntervalMin(value: Int) {
            dataStore.edit { it[Keys.NAG_INTERVAL_MIN] = value }
        }

        override suspend fun setNagMaxCount(value: Int) {
            dataStore.edit { it[Keys.NAG_MAX_COUNT] = value }
        }

        override suspend fun setSnoozeMin(value: Int) {
            dataStore.edit { it[Keys.SNOOZE_MIN] = value }
        }

        override suspend fun setMissedGraceMin(value: Int) {
            dataStore.edit { it[Keys.MISSED_GRACE_MIN] = value }
        }

        override suspend fun setTheme(value: ThemeMode) {
            dataStore.edit { it[Keys.THEME] = value.name }
        }

        override suspend fun setDynamicColor(value: Boolean) {
            dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
        }

        override suspend fun setLanguage(value: AppLanguage) {
            dataStore.edit { it[Keys.LANGUAGE] = value.name }
        }

        override suspend fun setLowStockNotifyEnabled(value: Boolean) {
            dataStore.edit { it[Keys.LOW_STOCK_NOTIFY] = value }
        }

        override suspend fun setOnboardingDone(value: Boolean) {
            dataStore.edit { it[Keys.ONBOARDING_DONE] = value }
        }

        override suspend fun setLastAutoBackupAt(value: Instant?) {
            dataStore.edit { p ->
                if (value == null) {
                    p.remove(Keys.LAST_AUTO_BACKUP_AT)
                } else {
                    p[Keys.LAST_AUTO_BACKUP_AT] = value.toEpochMilli()
                }
            }
        }

        override suspend fun replaceAll(settings: AppSettings) {
            dataStore.edit { p ->
                p.clear()
                settings.activeProfileId?.let { p[Keys.ACTIVE_PROFILE_ID] = it }
                p[Keys.NAG_INTERVAL_MIN] = settings.nagIntervalMin
                p[Keys.NAG_MAX_COUNT] = settings.nagMaxCount
                p[Keys.SNOOZE_MIN] = settings.snoozeMin
                p[Keys.MISSED_GRACE_MIN] = settings.missedGraceMin
                p[Keys.THEME] = settings.theme.name
                p[Keys.DYNAMIC_COLOR] = settings.dynamicColor
                p[Keys.LANGUAGE] = settings.language.name
                p[Keys.LOW_STOCK_NOTIFY] = settings.lowStockNotifyEnabled
                p[Keys.ONBOARDING_DONE] = settings.onboardingDone
                settings.lastAutoBackupAt?.let { p[Keys.LAST_AUTO_BACKUP_AT] = it.toEpochMilli() }
            }
        }
    }
