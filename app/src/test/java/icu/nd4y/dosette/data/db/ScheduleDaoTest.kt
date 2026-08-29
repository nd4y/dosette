package icu.nd4y.dosette.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ScheduleDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
        runTest {
            db.profileDao().upsert(profileEntity())
            db.medicationDao().upsert(medicationEntity())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `times come back with schedule`() =
        runTest {
            db.scheduleDao().insertWithTimes(
                scheduleEntity(),
                listOf(
                    scheduleTimeEntity(id = "t2", timeMinutes = 20 * 60, sortIndex = 1),
                    scheduleTimeEntity(id = "t1", timeMinutes = 8 * 60, sortIndex = 0),
                ),
            )
            val loaded = db.scheduleDao().getByMedication("m1").single()
            assertThat(loaded.times).hasSize(2)
        }

    @Test
    fun `edit closes old version and inserts new one`() =
        runTest {
            db.scheduleDao().insertWithTimes(scheduleEntity(id = "s1"), listOf(scheduleTimeEntity(id = "t1")))

            val editDay = LocalDate.parse("2026-08-29")
            db.scheduleDao().replaceActive(
                currentId = "s1",
                closeOn = editDay.minusDays(1),
                replacement = scheduleEntity(id = "s2", startDate = editDay),
                replacementTimes = listOf(scheduleTimeEntity(id = "t2", scheduleId = "s2", timeMinutes = 9 * 60)),
            )

            val versions = db.scheduleDao().getByMedication("m1")
            assertThat(versions).hasSize(2)

            val yesterday = db.scheduleDao().getActiveOn(editDay.minusDays(1)).map { it.schedule.id }
            val today = db.scheduleDao().getActiveOn(editDay).map { it.schedule.id }
            assertThat(yesterday).containsExactly("s1")
            assertThat(today).containsExactly("s2")
        }

    @Test
    fun `getActiveOn respects inclusive bounds`() =
        runTest {
            db.scheduleDao().insertWithTimes(
                scheduleEntity(
                    startDate = LocalDate.parse("2026-08-01"),
                    endDate = LocalDate.parse("2026-08-31"),
                ),
                listOf(scheduleTimeEntity()),
            )

            assertThat(db.scheduleDao().getActiveOn(LocalDate.parse("2026-07-31"))).isEmpty()
            assertThat(db.scheduleDao().getActiveOn(LocalDate.parse("2026-08-01"))).hasSize(1)
            assertThat(db.scheduleDao().getActiveOn(LocalDate.parse("2026-08-31"))).hasSize(1)
            assertThat(db.scheduleDao().getActiveOn(LocalDate.parse("2026-09-01"))).isEmpty()
        }
}
