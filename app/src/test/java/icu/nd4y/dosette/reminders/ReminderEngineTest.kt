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
import org.robolectric.shadows.ShadowAlarmManager
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

    override fun cancelAppointment(
        appointmentId: String,
        offsetsMin: List<Int>,
    ) = Unit
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

    override suspend fun setAlarmClock(value: Boolean) = Unit

    override suspend fun setOnboardingDone(value: Boolean) = Unit

    override suspend fun setLastAutoBackupAt(value: Instant?) = Unit

    override suspend fun setLastAppointmentSweepAt(value: Instant) {
        state.value = state.value.copy(lastAppointmentSweepAt = value)
    }

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
        doseLogRepository = DoseLogRepositoryImpl(db, db.doseLogDao(), db.medicationVariantDao())
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
                widgetRefresher = { },
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
    fun `repeated take does not drain the stock twice`() =
        runTest {
            engine.processDueEvents()
            engine.onUserAction(key, UserDoseAction.TAKE)
            engine.onUserAction(key, UserDoseAction.TAKE)

            assertThat(doseLogRepository.getScheduledInRange(key.date, key.date)).hasSize(1)
            // One 150 mg dose out of 75 mg capsules = 2 units, exactly once.
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(8.0)
        }

    @Test
    fun `flipping a taken dose to skipped returns the consumed stock`() =
        runTest {
            engine.processDueEvents()
            engine.onUserAction(key, UserDoseAction.TAKE)
            engine.onUserAction(key, UserDoseAction.SKIP)

            val log = doseLogRepository.getScheduledInRange(key.date, key.date).single()
            assertThat(log.status).isEqualTo(DoseStatus.SKIPPED)
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `take decrements by the amount of its own slot`() =
        runTest {
            // Second slot at 20:00 carries a double dose.
            db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s2", startDate = key.date),
                listOf(
                    scheduleTimeEntity(id = "t-evening", scheduleId = "s2", timeMinutes = 20 * 60, doseAmount = 2.0),
                ),
            )
            val evening = OccurrenceKey("m1", key.date, LocalTime.of(20, 0))
            clock.current =
                key.date
                    .atTime(20, 0)
                    .atZone(zone)
                    .toInstant()
                    .plusSeconds(5)

            engine.processDueEvents()
            engine.onUserAction(evening, UserDoseAction.TAKE)

            val log =
                doseLogRepository
                    .getScheduledInRange(key.date, key.date)
                    .single { it.time == LocalTime.of(20, 0) }
            // 2 x 150 mg out of 75 mg capsules = 4 units, both in the log
            // and on the shelf.
            assertThat(log.consumedUnits).isEqualTo(4.0)
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(6.0)
        }

    @Test
    fun `state whose medication is gone is cancelled instead of nagging forever`() =
        runTest {
            engine.processDueEvents()
            assertThat(stateRepository.get(key)).isNotNull()

            db.medicationDao().delete("m1")
            engine.processDueEvents()

            assertThat(stateRepository.get(key)).isNull()
            assertThat(notifier.cancelled).contains(key)
        }

    @Test
    fun `appointment reminder is not re-posted by a later pass`() =
        runTest {
            AppointmentRepositoryImpl(db.appointmentDao()).upsert(
                Appointment(
                    id = "a1",
                    profileId = "p1",
                    title = "Visit",
                    doctorName = null,
                    location = null,
                    date = key.date,
                    time = LocalTime.of(9, 0),
                    notes = null,
                    reminderOffsetsMin = listOf(55),
                    createdAt = clock.instant(),
                ),
            )

            // Reminder due at 08:05 (55 min before 09:00) — move there.
            clock.advance(Duration.ofMinutes(5))
            engine.processDueEvents()
            assertThat(notifier.appointments).hasSize(1)

            // A pass seconds later inside the window must not re-alert.
            clock.advance(Duration.ofSeconds(90))
            engine.processDueEvents()
            assertThat(notifier.appointments).hasSize(1)
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
    fun `undo of a take restores stock and brings the reminder back inside grace`() =
        runTest {
            engine.processDueEvents()
            engine.onUserAction(key, UserDoseAction.TAKE)
            notifier.reminders.clear()

            engine.undoDose(key)

            assertThat(doseLogRepository.getScheduledInRange(key.date, key.date)).isEmpty()
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(10.0)
            // Still inside the grace window, so the reminder returns audibly.
            assertThat(stateRepository.get(key)?.phase).isEqualTo(ReminderPhase.ACTIVE)
            assertThat(notifier.reminders.single().second).isTrue()
        }

    @Test
    fun `undo past the grace window quietly finalizes as missed`() =
        runTest {
            engine.processDueEvents()
            engine.onUserAction(key, UserDoseAction.TAKE)
            clock.advance(Duration.ofMinutes(61))
            notifier.reminders.clear()

            engine.undoDose(key)

            val log = doseLogRepository.getScheduledInRange(key.date, key.date).single()
            assertThat(log.status).isEqualTo(DoseStatus.MISSED)
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(10.0)
            assertThat(notifier.reminders).isEmpty()
        }

    @Test
    fun `deleting a one-off schedule removes its state log and stock effect`() =
        runTest {
            // One-off dose at 08:30 the same day, alongside the regular 08:00 slot.
            db.scheduleDao().insertWithTimes(
                scheduleEntity(
                    id = "s-oneoff",
                    oneOff = true,
                    startDate = key.date,
                    endDate = key.date,
                ),
                listOf(scheduleTimeEntity(id = "t-oneoff", scheduleId = "s-oneoff", timeMinutes = 8 * 60 + 30)),
            )
            val oneOffKey = OccurrenceKey("m1", key.date, LocalTime.of(8, 30))
            clock.advance(Duration.ofMinutes(30))
            engine.processDueEvents()
            engine.onUserAction(oneOffKey, UserDoseAction.TAKE)
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(8.0)

            engine.deleteOneOffSchedule("m1", "s-oneoff")

            assertThat(doseLogRepository.getScheduled(oneOffKey)).isNull()
            assertThat(stateRepository.get(oneOffKey)).isNull()
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(10.0)
            // The regular 08:00 slot is untouched.
            assertThat(stateRepository.get(key)?.phase).isEqualTo(ReminderPhase.ACTIVE)
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

    @Test
    fun `state whose slot vanished in a schedule edit is cancelled`() =
        runTest {
            engine.processDueEvents()
            assertThat(stateRepository.get(key)).isNotNull()

            // The 08:00 slot moves to 09:00 — a same-day edit swaps the version.
            db.scheduleDao().delete("s1")
            db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s2", startDate = LocalDate.parse("2026-08-29")),
                listOf(scheduleTimeEntity(id = "t2", scheduleId = "s2", timeMinutes = 9 * 60)),
            )
            engine.reschedule()

            assertThat(stateRepository.get(key)).isNull()
            assertThat(notifier.cancelled).contains(key)
            // Nothing is finalized for a slot that no longer exists.
            clock.advance(Duration.ofHours(3))
            engine.processDueEvents()
            assertThat(doseLogRepository.getScheduled(key)).isNull()
        }

    private fun appointmentAtNine(
        id: String,
        offsetMin: Int,
    ) = Appointment(
        id = id,
        profileId = "p1",
        title = "Visit",
        doctorName = null,
        location = null,
        date = key.date,
        time = LocalTime.of(9, 0),
        notes = null,
        reminderOffsetsMin = listOf(offsetMin),
        createdAt = clock.instant(),
    )

    @Test
    fun `appointment reminder is still posted when the pass runs late`() =
        runTest {
            val appointments = AppointmentRepositoryImpl(db.appointmentDao())
            appointments.upsert(appointmentAtNine(id = "a1", offsetMin = 55))
            // Due at 08:05; the phone was off until 08:25.
            clock.advance(Duration.ofMinutes(25))
            engine.processDueEvents()
            assertThat(notifier.appointments).hasSize(1)

            // Once the visit has started there is nothing left to remind about.
            appointments.upsert(appointmentAtNine(id = "a2", offsetMin = 30))
            clock.advance(Duration.ofMinutes(60))
            engine.processDueEvents()
            assertThat(notifier.appointments).hasSize(1)
        }

    @Test
    fun `a slot older than its version is neither alerted nor finalized`() =
        runTest {
            // Medication set up at 08:00:05 with a 07:00 slot: that dose was
            // never planned, so no reminder and no missed log for it.
            db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s-late", startDate = key.date, createdAt = clock.instant()),
                listOf(scheduleTimeEntity(id = "t-late", scheduleId = "s-late", timeMinutes = 7 * 60)),
            )
            val earlyKey = OccurrenceKey("m1", key.date, LocalTime.of(7, 0))

            engine.processDueEvents()
            assertThat(stateRepository.get(key)).isNotNull()
            assertThat(stateRepository.get(earlyKey)).isNull()

            clock.advance(Duration.ofHours(3))
            engine.processDueEvents()
            assertThat(doseLogRepository.getScheduled(earlyKey)).isNull()
        }

    @Test
    fun `housekeeping alone does not raise the alarm-clock icon`() =
        runTest {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            db.medicationDao().delete("m1")
            engine.reschedule()

            val alarmManager =
                ApplicationProvider.getApplicationContext<Context>().getSystemService(AlarmManager::class.java)
            val next = requireNotNull(shadowOf(alarmManager).nextScheduledAlarm)
            assertThat(next.alarmClockInfo).isNull()
            assertThat(next.isAllowWhileIdle).isTrue()
        }

    @Test
    fun `an archived medication's earlier doses are finalized, never alerted`() =
        runTest {
            // Yesterday's 08:00 was never marked; the medication is archived today.
            db.scheduleDao().delete("s1")
            db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s-old", startDate = key.date.minusDays(1)),
                listOf(scheduleTimeEntity(id = "t-old", scheduleId = "s-old", timeMinutes = 8 * 60)),
            )
            db.medicationDao().archive("m1", clock.instant())
            val yesterdayKey = OccurrenceKey("m1", key.date.minusDays(1), LocalTime.of(8, 0))

            engine.processDueEvents()

            assertThat(doseLogRepository.getScheduled(yesterdayKey)?.status).isEqualTo(DoseStatus.MISSED)
            // From the archive day on there is no plan at all.
            assertThat(doseLogRepository.getScheduled(key)).isNull()
            assertThat(notifier.reminders).isEmpty()
        }

    @Test
    fun `a version without reminders is swept for missed doses but never alerts`() =
        runTest {
            db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s-quiet", startDate = key.date).copy(remindersEnabled = false),
                listOf(scheduleTimeEntity(id = "t-quiet", scheduleId = "s-quiet", timeMinutes = 7 * 60)),
            )
            val quietKey = OccurrenceKey("m1", key.date, LocalTime.of(7, 0))
            clock.advance(Duration.ofHours(3))

            engine.processDueEvents()

            assertThat(doseLogRepository.getScheduled(quietKey)?.status).isEqualTo(DoseStatus.MISSED)
            assertThat(notifier.reminders.map { it.first.key }).doesNotContain(quietKey)
        }

    @Test
    fun `undo of a mark older than the sweep window finalizes it as missed`() =
        runTest {
            val oldDate = key.date.minusDays(10)
            db.scheduleDao().delete("s1")
            db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s-old", startDate = oldDate),
                listOf(scheduleTimeEntity(id = "t-old", scheduleId = "s-old", timeMinutes = 8 * 60)),
            )
            val oldKey = OccurrenceKey("m1", oldDate, LocalTime.of(8, 0))
            engine.onUserAction(oldKey, UserDoseAction.TAKE)
            assertThat(doseLogRepository.getScheduled(oldKey)?.status).isEqualTo(DoseStatus.TAKEN)

            engine.undoDose(oldKey)

            assertThat(doseLogRepository.getScheduled(oldKey)?.status).isEqualTo(DoseStatus.MISSED)
        }
}
