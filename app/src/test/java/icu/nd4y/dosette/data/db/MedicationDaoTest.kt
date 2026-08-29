package icu.nd4y.dosette.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MedicationDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
        runTest {
            db.profileDao().upsert(profileEntity())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `details carry schedules with times and inventory`() =
        runTest {
            db.medicationDao().upsert(medicationEntity())
            db.scheduleDao().insertWithTimes(
                scheduleEntity(),
                listOf(
                    scheduleTimeEntity(id = "t1", timeMinutes = 8 * 60, sortIndex = 0),
                    scheduleTimeEntity(id = "t2", timeMinutes = 20 * 60, sortIndex = 1),
                ),
            )
            db.inventoryDao().upsert(inventoryEntity())

            val details = db.medicationDao().getAllActiveWithDetails().single()
            assertThat(details.schedules.single().times).hasSize(2)
            assertThat(details.inventory?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `archived medications drop out of active list`() =
        runTest {
            db.medicationDao().upsert(medicationEntity(id = "m1"))
            db.medicationDao().upsert(medicationEntity(id = "m2", name = "Lisinopril"))
            db.medicationDao().archive("m1", testInstant)

            val active = db.medicationDao().getAllActiveWithDetails().map { it.medication.id }
            assertThat(active).containsExactly("m2")

            db.medicationDao().unarchive("m1")
            assertThat(db.medicationDao().getAllActiveWithDetails()).hasSize(2)
        }
}
