package icu.nd4y.dosette.widget

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.doseLogEntity
import icu.nd4y.dosette.data.db.inMemoryDb
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.scheduleTimeEntity
import icu.nd4y.dosette.data.repository.DoseLogRepositoryImpl
import icu.nd4y.dosette.data.repository.MedicationRepositoryImpl
import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.ui.today.DoseUiStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private class FixedSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings(activeProfileId = "p1"))

    override suspend fun setActiveProfileId(id: String?) = Unit

    override suspend fun setNagIntervalMin(value: Int) = Unit

    override suspend fun setNagMaxCount(value: Int) = Unit

    override suspend fun setSnoozeMin(value: Int) = Unit

    override suspend fun setMissedGraceMin(value: Int) = Unit

    override suspend fun setTheme(value: ThemeMode) = Unit

    override suspend fun setDynamicColor(value: Boolean) = Unit

    override suspend fun setLanguage(value: AppLanguage) = Unit

    override suspend fun setLowStockNotifyEnabled(value: Boolean) = Unit

    override suspend fun setOnboardingDone(value: Boolean) = Unit

    override suspend fun setLastAutoBackupAt(value: Instant?) = Unit

    override suspend fun setLastAppointmentSweepAt(value: Instant) = Unit

    override suspend fun setPlace(
        id: PlaceId,
        config: PlaceConfig?,
    ) = Unit

    override suspend fun replaceAll(settings: AppSettings) = Unit
}

@RunWith(RobolectricTestRunner::class)
class WidgetStateLoaderTest {
    private val zone = ZoneId.of("Europe/Moscow")

    // 2026-08-29 10:00 local; doses at 08:00 (taken) and 20:00 (pending).
    private val now: Instant =
        LocalDate
            .parse("2026-08-29")
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()

    private lateinit var db: AppDatabase
    private lateinit var loader: WidgetStateLoader

    @Before
    fun setUp() {
        db = inMemoryDb()
        loader =
            WidgetStateLoader(
                medicationRepository =
                    MedicationRepositoryImpl(db.medicationDao(), db.scheduleDao(), db.medicationVariantDao()),
                doseLogRepository = DoseLogRepositoryImpl(db, db.doseLogDao(), db.medicationVariantDao()),
                settingsRepository = FixedSettingsRepository(),
                clock = Clock.fixed(now, zone),
            )
        runTest {
            db.profileDao().upsert(profileEntity())
            db.medicationDao().upsert(medicationEntity())
            db.scheduleDao().insertWithTimes(
                scheduleEntity(),
                listOf(
                    scheduleTimeEntity(id = "t-morning", timeMinutes = 8 * 60),
                    scheduleTimeEntity(id = "t-evening", timeMinutes = 20 * 60),
                ),
            )
            db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s-prn").copy(type = "AS_NEEDED", remindersEnabled = false),
                emptyList(),
            )
            db.doseLogDao().insert(doseLogEntity(id = "taken-morning", timeMinutes = 8 * 60, status = "TAKEN"))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `loads ring counts next slot and prn`() =
        runTest {
            val state = loader.load()

            assertThat(state.planned).isEqualTo(2)
            assertThat(state.taken).isEqualTo(1)

            val next = state.nextSlotDoses.single()
            assertThat(next.time).isEqualTo(LocalTime.of(20, 0))
            assertThat(next.status).isEqualTo(DoseUiStatus.PENDING)
            // 10:00 -> 20:00.
            assertThat(state.minutesToNext).isEqualTo(10 * 60L)

            assertThat(state.prn.single().medicationId).isEqualTo("m1")
        }

    @Test
    fun `empty state without an active profile falls back gracefully`() =
        runTest {
            val emptyLoader =
                WidgetStateLoader(
                    medicationRepository =
                        MedicationRepositoryImpl(db.medicationDao(), db.scheduleDao(), db.medicationVariantDao()),
                    doseLogRepository = DoseLogRepositoryImpl(db, db.doseLogDao(), db.medicationVariantDao()),
                    settingsRepository =
                        object : SettingsRepository by FixedSettingsRepository() {
                            override val settings = MutableStateFlow(AppSettings())
                        },
                    clock = Clock.fixed(now, zone),
                )

            val state = emptyLoader.load()

            assertThat(state.planned).isEqualTo(0)
            assertThat(state.nextSlotDoses).isEmpty()
            assertThat(state.minutesToNext).isNull()
        }
}
