package icu.nd4y.dosette.testing

import androidx.test.core.app.ApplicationProvider
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.inMemoryDb
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.repository.AppointmentRepositoryImpl
import icu.nd4y.dosette.data.repository.DoseLogRepositoryImpl
import icu.nd4y.dosette.data.repository.MedicationRepositoryImpl
import icu.nd4y.dosette.data.repository.ProfileRepositoryImpl
import icu.nd4y.dosette.data.repository.ReminderStateRepositoryImpl
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.reminders.AlarmScheduler
import icu.nd4y.dosette.reminders.ReminderEngine
import java.time.Instant
import java.time.ZoneId

/**
 * A real [ReminderEngine] over an in-memory database with every Android
 * boundary faked and a clock the test can move. ViewModel tests build on it
 * so a `reschedule()` runs the same passes the app does instead of a stub.
 * Runs under Robolectric (Room and the alarm manager need a context).
 */
class TestEngine(
    now: Instant = testInstant,
    zone: ZoneId = ZoneId.of("Europe/Moscow"),
    settings: AppSettings = AppSettings(),
) : AutoCloseable {
    val db: AppDatabase = inMemoryDb()
    val clock = MutableClock(now, zone)
    val settingsRepository = FakeSettingsRepository(settings)
    val notifier = FakeReminderNotifier()
    val placeMonitor = FakePlaceMonitor()
    val widgetRefresher = FakeWidgetRefresher()

    val profileRepository = ProfileRepositoryImpl(db.profileDao())
    val appointmentRepository = AppointmentRepositoryImpl(db.appointmentDao())
    val medicationRepository = MedicationRepositoryImpl(db.medicationDao(), db.scheduleDao(), db.medicationVariantDao())
    val doseLogRepository = DoseLogRepositoryImpl(db, db.doseLogDao(), db.medicationVariantDao())
    val reminderStateRepository = ReminderStateRepositoryImpl(db.reminderStateDao())

    val engine =
        ReminderEngine(
            medicationRepository = medicationRepository,
            doseLogRepository = doseLogRepository,
            reminderStateRepository = reminderStateRepository,
            appointmentRepository = appointmentRepository,
            settingsRepository = settingsRepository,
            profileRepository = profileRepository,
            notifier = notifier,
            alarmScheduler = AlarmScheduler(ApplicationProvider.getApplicationContext()),
            placeMonitor = placeMonitor,
            widgetRefresher = widgetRefresher,
            clock = clock,
        )

    override fun close() {
        db.close()
    }
}
