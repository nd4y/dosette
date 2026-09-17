package icu.nd4y.dosette.watch

import icu.nd4y.dosette.data.repository.ProfileRepository
import icu.nd4y.dosette.data.repository.ReminderStateRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.link.WatchJson
import icu.nd4y.dosette.widget.WidgetStateLoader
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends the watch the same picture the widget renders — the active
 * profile's day — after every engine pass. The Data Layer keeps the last
 * item, so a watch that was out of range simply reads it when it is back.
 */
@Singleton
class WatchPublisher
    @Inject
    constructor(
        private val stateLoader: WidgetStateLoader,
        private val reminderStateRepository: ReminderStateRepository,
        private val profileRepository: ProfileRepository,
        private val settingsRepository: SettingsRepository,
        private val link: WatchLink,
    ) {
        suspend fun publish() {
            val state = stateLoader.load()
            val ringing =
                reminderStateRepository
                    .getAll()
                    .filter { it.phase == ReminderPhase.ACTIVE }
                    .mapTo(HashSet()) { it.occurrenceKey }
            val profiles = profileRepository.getAll()
            val profileName =
                if (profiles.size > 1) {
                    val activeId = settingsRepository.settings.first().activeProfileId
                    profiles.firstOrNull { it.id == activeId }?.name
                } else {
                    null
                }
            link.publishSnapshot(WatchJson.encode(buildWatchSnapshot(state, ringing, profileName)))
        }
    }
