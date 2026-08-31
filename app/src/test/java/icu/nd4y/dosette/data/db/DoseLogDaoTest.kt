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
class DoseLogDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
        runTest {
            db.profileDao().upsert(profileEntity())
            db.medicationDao().upsert(medicationEntity())
            db.scheduleDao().insertWithTimes(scheduleEntity(), listOf(scheduleTimeEntity()))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insertScheduledIfAbsent is idempotent per occurrence`() =
        runTest {
            val first = db.doseLogDao().insertScheduledIfAbsent(doseLogEntity(id = "d1", status = "MISSED"))
            val second = db.doseLogDao().insertScheduledIfAbsent(doseLogEntity(id = "d2", status = "MISSED"))

            assertThat(first).isTrue()
            assertThat(second).isFalse()
            assertThat(
                db.doseLogDao().getScheduledInRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31")),
            ).hasSize(1)
        }

    @Test
    fun `same time different day is a different occurrence`() =
        runTest {
            val d1 = db.doseLogDao().insertScheduledIfAbsent(doseLogEntity(id = "d1"))
            val d2 =
                db.doseLogDao().insertScheduledIfAbsent(
                    doseLogEntity(id = "d2", date = LocalDate.parse("2026-08-30")),
                )
            assertThat(d1).isTrue()
            assertThat(d2).isTrue()
        }

    @Test
    fun `prn logs do not collide with scheduled identity`() =
        runTest {
            db.doseLogDao().insert(doseLogEntity(id = "prn1", kind = "PRN", timeMinutes = null))
            val scheduled = db.doseLogDao().insertScheduledIfAbsent(doseLogEntity(id = "d1"))
            assertThat(scheduled).isTrue()
        }

    @Test
    fun `upsert flips status in place`() =
        runTest {
            val log = doseLogEntity(id = "d1", status = "MISSED")
            db.doseLogDao().insert(log)
            db.doseLogDao().upsert(log.copy(status = "TAKEN"))

            assertThat(db.doseLogDao().getById("d1")?.status).isEqualTo("TAKEN")
        }
}
