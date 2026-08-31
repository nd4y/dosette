package icu.nd4y.dosette.data.repository

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.inMemoryDb
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.variantEntity
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DoseLogRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: DoseLogRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryDb()
        repository = DoseLogRepositoryImpl(db, db.doseLogDao(), db.medicationVariantDao())
        runTest {
            db.profileDao().upsert(profileEntity())
            db.medicationDao().upsert(medicationEntity())
            db.medicationVariantDao().upsert(variantEntity(currentStock = 10.0))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun prnLog(id: String = "prn1") =
        DoseLog(
            id = id,
            profileId = "p1",
            medicationId = "m1",
            scheduleId = null,
            kind = DoseKind.PRN,
            date = LocalDate.parse("2026-08-30"),
            time = null,
            scheduledAt = null,
            status = DoseStatus.TAKEN,
            actedAt = Instant.parse("2026-08-30T10:00:00Z"),
            amount = 1.0,
            variantId = "v1",
            consumedUnits = 2.0,
            note = null,
            updatedAt = Instant.parse("2026-08-30T10:00:00Z"),
        )

    @Test
    fun `undoPrn removes the log and returns the consumed stock`() =
        runTest {
            repository.recordPrn(prnLog())
            assertThat(db.medicationVariantDao().getById("v1")?.currentStock).isEqualTo(8.0)

            repository.undoPrn("prn1")

            assertThat(repository.getById("prn1")).isNull()
            assertThat(db.medicationVariantDao().getById("v1")?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `undoPrn refuses a scheduled log`() =
        runTest {
            repository.recordScheduledIfAbsent(
                prnLog(id = "sched1").copy(
                    kind = DoseKind.SCHEDULED,
                    time = java.time.LocalTime.of(8, 0),
                ),
            )

            repository.undoPrn("sched1")

            assertThat(repository.getById("sched1")).isNotNull()
        }
}
