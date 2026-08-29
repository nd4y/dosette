package icu.nd4y.dosette.data.backup

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.dao.BackupEntities
import icu.nd4y.dosette.data.db.doseLogEntity
import icu.nd4y.dosette.data.db.entity.AppointmentEntity
import icu.nd4y.dosette.data.db.entity.ReminderStateEntity
import icu.nd4y.dosette.data.db.inMemoryDb
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.scheduleTimeEntity
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.db.variantEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BackupDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun appointmentEntity(
        id: String,
        profileId: String,
    ) = AppointmentEntity(
        id = id,
        profileId = profileId,
        title = "Визит",
        doctorName = null,
        location = null,
        date = LocalDate.parse("2026-09-02"),
        timeMinutes = 9 * 60,
        notes = null,
        reminderOffsetsMin = "120",
        createdAt = testInstant,
    )

    @Test
    fun `replaceAll swaps the whole dataset and clears reminder states`() =
        runTest {
            val backupDao = db.backupDao()

            // Old dataset, including a live reminder state.
            db.profileDao().upsert(profileEntity(id = "old-p"))
            db.medicationDao().upsert(medicationEntity(id = "old-m", profileId = "old-p"))
            db.medicationVariantDao().upsert(variantEntity(id = "old-v", medicationId = "old-m"))
            db.scheduleDao().insert(scheduleEntity(id = "old-s", medicationId = "old-m"))
            db.scheduleDao().insertTimes(listOf(scheduleTimeEntity(id = "old-t", scheduleId = "old-s")))
            db.doseLogDao().upsert(doseLogEntity(id = "old-d", profileId = "old-p", medicationId = "old-m"))
            db.appointmentDao().upsert(appointmentEntity("old-a", "old-p"))
            db.reminderStateDao().upsert(
                ReminderStateEntity(
                    occurrenceKey = "old-m|2026-08-29|08:00",
                    medicationId = "old-m",
                    profileId = "old-p",
                    scheduledAt = testInstant,
                    phase = "ACTIVE",
                    snoozedUntil = null,
                    nagCount = 1,
                    firstNotifiedAt = testInstant,
                    lastAlertAt = testInstant,
                ),
            )

            backupDao.replaceAll(
                BackupEntities(
                    profiles = listOf(profileEntity(id = "new-p", name = "Imported")),
                    medications = listOf(medicationEntity(id = "new-m", profileId = "new-p")),
                    variants = listOf(variantEntity(id = "new-v", medicationId = "new-m")),
                    schedules = listOf(scheduleEntity(id = "new-s", medicationId = "new-m")),
                    scheduleTimes = listOf(scheduleTimeEntity(id = "new-t", scheduleId = "new-s")),
                    doseLogs = listOf(doseLogEntity(id = "new-d", profileId = "new-p", medicationId = "new-m")),
                    appointments = listOf(appointmentEntity("new-a", "new-p")),
                ),
            )

            assertThat(backupDao.profiles().map { it.id }).containsExactly("new-p")
            assertThat(backupDao.medications().map { it.id }).containsExactly("new-m")
            assertThat(backupDao.variants().map { it.id }).containsExactly("new-v")
            assertThat(backupDao.schedules().map { it.id }).containsExactly("new-s")
            assertThat(backupDao.scheduleTimes().map { it.id }).containsExactly("new-t")
            assertThat(backupDao.doseLogs().map { it.id }).containsExactly("new-d")
            assertThat(backupDao.appointments().map { it.id }).containsExactly("new-a")
            assertThat(db.reminderStateDao().getAll()).isEmpty()
        }

    @Test
    fun `replaceAll into an empty database works`() =
        runTest {
            db.backupDao().replaceAll(
                BackupEntities(
                    profiles = listOf(profileEntity(id = "p")),
                    medications = emptyList(),
                    variants = emptyList(),
                    schedules = emptyList(),
                    scheduleTimes = emptyList(),
                    doseLogs = emptyList(),
                    appointments = emptyList(),
                ),
            )
            assertThat(db.backupDao().profiles().map { it.id }).containsExactly("p")
        }
}
