package icu.nd4y.dosette.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.inMemoryDb
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.scheduleTimeEntity
import icu.nd4y.dosette.data.db.variantEntity
import icu.nd4y.dosette.data.repository.AppointmentRepositoryImpl
import icu.nd4y.dosette.data.repository.DoseLogRepositoryImpl
import icu.nd4y.dosette.data.repository.MedicationRepositoryImpl
import icu.nd4y.dosette.data.repository.ProfileRepositoryImpl
import icu.nd4y.dosette.data.repository.ReminderStateRepositoryImpl
import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import icu.nd4y.dosette.reminders.notifications.ReminderPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private class MutableClock(
    var current: Instant,
    private val zoneId: ZoneId,
) : Clock() {
    override fun instant(): Instant = current

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    fun advance(duration: Duration) {
        current += duration
    }
}

private class FakeNotifier : ReminderNotifier {
    val reminders = mutableListOf<Pair<ReminderPayload, Boolean>>()
    val cancelled = mutableListOf<OccurrenceKey>()
    val missedNotices = mutableListOf<OccurrenceKey>()
    val lowStock = mutableListOf<String>()
    val appointments = mutableListOf<Pair<Appointment, Int>>()

    override fun postReminder(
        payload: ReminderPayload,
        alert: Boolean,
    ) {
        reminders += payload to alert
    }

    override fun cancelReminder(key: OccurrenceKey) {
        cancelled += key
    }

    override fun postMissedNotice(
        key: OccurrenceKey,
        medicationTitle: String,
    ) {
        missedNotices += key
    }

    override fun postLowStock(
        medicationId: String,
        medicationTitle: String,
        unitsLeft: String,
    ) {
        lowStock += medicationId
    }

    override fun postAppointment(
        appointment: Appointment,
        offsetMin: Int,
    ) {
        appointments += appointment to offsetMin
    }

    override fun cancelAll() = Unit
}

private class FakePlaceMonitor : icu.nd4y.dosette.reminders.places.PlaceMonitor {
    var currentlyAt = false
    var synced: Map<icu.nd4y.dosette.domain.model.PlaceId, icu.nd4y.dosette.domain.model.PlaceConfig> = emptyMap()

    override fun isCurrentlyAt(config: icu.nd4y.dosette.domain.model.PlaceConfig): Boolean = currentlyAt

    override fun syncGeofences(
        places: Map<icu.nd4y.dosette.domain.model.PlaceId, icu.nd4y.dosette.domain.model.PlaceConfig>,
    ) {
        synced = places
    }
}

private class FakeSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(AppSettings())

    override val settings = state

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

    override suspend fun setPlace(
        id: icu.nd4y.dosette.domain.model.PlaceId,
        config: icu.nd4y.dosette.domain.model.PlaceConfig?,
    ) = Unit

    override suspend fun replaceAll(settings: AppSettings) = Unit
}

@RunWith(RobolectricTestRunner::class)
class ReminderEngineTest {
    private val zone = ZoneId.of("Europe/Moscow")

    // 2026-08-29, dose slot at 08:00 local.
    private val doseInstant: Instant =
        LocalDate
            .parse("2026-08-29")
            .atTime(8, 0)
            .atZone(zone)
            .toInstant()
    private val key = OccurrenceKey("m1", LocalDate.parse("2026-08-29"), LocalTime.of(8, 0))

    private lateinit var db: AppDatabase
    private lateinit var clock: MutableClock
    private lateinit var notifier: FakeNotifier
    private lateinit var engine: ReminderEngine
    private lateinit var stateRepository: ReminderStateRepositoryImpl
    private lateinit var doseLogRepository: DoseLogRepositoryImpl
    private lateinit var medicationRepository: MedicationRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryDb()
        clock = MutableClock(doseInstant.plusSeconds(5), zone)
        notifier = FakeNotifier()
        stateRepository = ReminderStateRepositoryImpl(db.reminderStateDao())
        doseLogRepository = DoseLogRepositoryImpl(db.doseLogDao(), db.medicationVariantDao())
        medicationRepository =
            MedicationRepositoryImpl(db.medicationDao(), db.scheduleDao(), db.medicationVariantDao())
        engine =
            ReminderEngine(
                medicationRepository = medicationRepository,
                doseLogRepository = doseLogRepository,
                reminderStateRepository = stateRepository,
                appointmentRepository = AppointmentRepositoryImpl(db.appointmentDao()),
                settingsRepository = FakeSettingsRepository(),
                profileRepository = ProfileRepositoryImpl(db.profileDao()),
                notifier = notifier,
                alarmScheduler = AlarmScheduler(ApplicationProvider.getApplicationContext()),
                placeMonitor = FakePlaceMonitor(),
                clock = clock,
            )
        runTest {
            db.profileDao().upsert(profileEntity())
            // 150 mg reference strength, stock in 75 mg capsules (the user's case).
            db.medicationDao().upsert(
                medicationEntity().copy(strengthValue = 150.0, defaultVariantId = "v75"),
            )
            db.medicationVariantDao().upsert(
                variantEntity(id = "v75", strengthValue = 75.0, currentStock = 10.0),
            )
            db.scheduleDao().insertWithTimes(
                scheduleEntity(startDate = LocalDate.parse("2026-08-29")),
                listOf(scheduleTimeEntity(timeMinutes = 8 * 60)),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `due occurrence posts an audible reminder and persists state`() =
        runTest {
            engine.processDueEvents()

            assertThat(notifier.reminders).hasSize(1)
            assertThat(notifier.reminders.single().second).isTrue()
            val state = stateRepository.get(key)
            assertThat(state?.phase).isEqualTo(ReminderPhase.ACTIVE)

            val alarmManager =
                ApplicationProvider.getApplicationContext<Context>().getSystemService(AlarmManager::class.java)
            assertThat(shadowOf(alarmManager).nextScheduledAlarm).isNotNull()
        }

    @Test
    fun `processing twice does not duplicate the reminder`() =
        runTest {
            engine.processDueEvents()
            engine.processDueEvents()

            assertThat(notifier.reminders).hasSize(1)
        }

    @Test
    fun `reschedule catches up an already-due occurrence`() =
        runTest {
            // Regression from the on-device import test: data lands while the
            // dose time is already in the past (inside grace) — reschedule()
            // alone must alert, without waiting for the next alarm tick.
            clock.advance(Duration.ofMinutes(10))
            engine.reschedule()

            assertThat(notifier.reminders).hasSize(1)
            assertThat(notifier.reminders.single().second).isTrue()
            assertThat(stateRepository.get(key)?.phase).isEqualTo(ReminderPhase.ACTIVE)
        }

    @Test
    fun `take writes the log and decrements the variant by strength ratio`() =
        runTest {
            engine.processDueEvents()
            engine.onUserAction(key, UserDoseAction.TAKE)

            val logs = doseLogRepository.getScheduledInRange(key.date, key.date)
            val log = logs.single()
            assertThat(log.status).isEqualTo(DoseStatus.TAKEN)
            assertThat(log.variantId).isEqualTo("v75")
            // 150 mg dose out of 75 mg capsules = 2 units.
            assertThat(log.consumedUnits).isEqualTo(2.0)

            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(8.0)
            assertThat(notifier.cancelled).contains(key)
            assertThat(stateRepository.get(key)).isNull()
        }

    @Test
    fun `skip writes the log without touching stock`() =
        runTest {
            engine.processDueEvents()
            engine.onUserAction(key, UserDoseAction.SKIP)

            val log = doseLogRepository.getScheduledInRange(key.date, key.date).single()
            assertThat(log.status).isEqualTo(DoseStatus.SKIPPED)
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `swipe dismiss reposts silently and keeps the state`() =
        runTest {
            engine.processDueEvents()
            engine.onDismissed(key)

            assertThat(notifier.reminders).hasSize(2)
            assertThat(notifier.reminders.last().second).isFalse()
            assertThat(stateRepository.get(key)?.phase).isEqualTo(ReminderPhase.ACTIVE)
        }

    @Test
    fun `nag tick re-alerts after the interval`() =
        runTest {
            engine.processDueEvents()
            clock.advance(Duration.ofMinutes(10))
            engine.processDueEvents()

            assertThat(notifier.reminders).hasSize(2)
            assertThat(notifier.reminders.last().second).isTrue()
            assertThat(stateRepository.get(key)?.nagCount).isEqualTo(1)
        }

    @Test
    fun `grace expiry finalizes as missed and posts a passive notice`() =
        runTest {
            engine.processDueEvents()
            clock.advance(Duration.ofMinutes(61))
            engine.processDueEvents()

            val log = doseLogRepository.getScheduledInRange(key.date, key.date).single()
            assertThat(log.status).isEqualTo(DoseStatus.MISSED)
            assertThat(notifier.missedNotices).contains(key)
            assertThat(stateRepository.get(key)).isNull()
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `snooze parks the reminder and expiry re-alerts`() =
        runTest {
            engine.processDueEvents()
            engine.onUserAction(key, UserDoseAction.SNOOZE)
            assertThat(stateRepository.get(key)?.phase).isEqualTo(ReminderPhase.SNOOZED)
            assertThat(notifier.cancelled).contains(key)

            clock.advance(Duration.ofMinutes(10))
            engine.processDueEvents()

            assertThat(stateRepository.get(key)?.phase).isEqualTo(ReminderPhase.ACTIVE)
            assertThat(notifier.reminders.last().second).isTrue()
        }

    @Test
    fun `low stock notice fires only on the downward crossing`() =
        runTest {
            // Threshold is 5; stock 10 -> 8 -> 6 -> 4 (crossing) -> 2.
            engine.processDueEvents()
            engine.onUserAction(key, UserDoseAction.TAKE)
            assertThat(notifier.lowStock).isEmpty()

            val nextDay = OccurrenceKey("m1", key.date.plusDays(1), key.time)
            clock.advance(Duration.ofDays(1))
            engine.processDueEvents()
            engine.onUserAction(nextDay, UserDoseAction.TAKE)
            assertThat(notifier.lowStock).isEmpty()

            val thirdDay = OccurrenceKey("m1", key.date.plusDays(2), key.time)
            clock.advance(Duration.ofDays(1))
            engine.processDueEvents()
            engine.onUserAction(thirdDay, UserDoseAction.TAKE)
            assertThat(notifier.lowStock).containsExactly("m1")

            val fourthDay = OccurrenceKey("m1", key.date.plusDays(3), key.time)
            clock.advance(Duration.ofDays(1))
            engine.processDueEvents()
            engine.onUserAction(fourthDay, UserDoseAction.TAKE)
            assertThat(notifier.lowStock).containsExactly("m1")
        }

    @Test
    fun `old unlogged occurrences are quietly finalized as missed`() =
        runTest {
            clock.advance(Duration.ofDays(2))
            engine.processDueEvents()

            val logs = doseLogRepository.getScheduledInRange(key.date, key.date.plusDays(2))
            assertThat(logs.filter { it.status == DoseStatus.MISSED }).isNotEmpty()
            // The long-gone dose must not raise an ongoing reminder.
            assertThat(notifier.reminders.map { it.first.key }).doesNotContain(key)
        }

    @Test
    fun `reconcile reposts active reminders silently`() =
        runTest {
            engine.processDueEvents()
            notifier.reminders.clear()

            engine.reconcile()

            assertThat(notifier.reminders.first().second).isFalse()
        }
}
