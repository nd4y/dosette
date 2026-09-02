package icu.nd4y.dosette.data

import icu.nd4y.dosette.data.repository.ProfileRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.Profile
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app is usable without onboarding: the first launch silently creates
 * one profile so every screen has a place to write to. Replaced by the
 * real onboarding flow later; safe to run on every start.
 */
@Singleton
class ProfileBootstrap
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) {
        suspend fun ensureDefaultProfile(defaultName: String) {
            val profiles = profileRepository.getAll()
            val profile =
                profiles.firstOrNull() ?: Profile(
                    id = UUID.randomUUID().toString(),
                    name = defaultName,
                    colorSeed = 0,
                    avatarKey = null,
                    sortOrder = 0,
                    createdAt = clock.instant(),
                ).also { profileRepository.upsert(it) }

            // Null on the first start; dangling when an import died between
            // the database swap and the settings write.
            val active = settingsRepository.settings.first().activeProfileId
            if (active == null || profiles.none { it.id == active }) {
                settingsRepository.setActiveProfileId(profile.id)
            }
        }
    }
