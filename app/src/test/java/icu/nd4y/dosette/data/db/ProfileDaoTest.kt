package icu.nd4y.dosette.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert and read back`() =
        runTest {
            db.profileDao().upsert(profileEntity())
            assertThat(db.profileDao().getById("p1")?.name).isEqualTo("Alex")
        }

    @Test
    fun `delete cascades to medications schedules logs and inventory`() =
        runTest {
            db.profileDao().upsert(profileEntity())
            db.medicationDao().upsert(medicationEntity())
            db.scheduleDao().insertWithTimes(scheduleEntity(), listOf(scheduleTimeEntity()))
            db.doseLogDao().insert(doseLogEntity())
            db.inventoryDao().upsert(inventoryEntity())

            db.profileDao().delete("p1")

            assertThat(db.medicationDao().getById("m1")).isNull()
            assertThat(db.scheduleDao().getByMedication("m1")).isEmpty()
            assertThat(db.doseLogDao().getById("d1")).isNull()
            assertThat(db.inventoryDao().getByMedication("m1")).isNull()
        }

    @Test
    fun `profiles ordered by sortOrder`() =
        runTest {
            db.profileDao().upsert(profileEntity(id = "p1", name = "Second").copy(sortOrder = 1))
            db.profileDao().upsert(profileEntity(id = "p2", name = "First").copy(sortOrder = 0))
            assertThat(db.profileDao().getAll().map { it.name }).containsExactly("First", "Second").inOrder()
        }
}
