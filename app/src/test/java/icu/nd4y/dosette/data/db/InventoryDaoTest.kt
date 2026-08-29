package icu.nd4y.dosette.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InventoryDaoTest {
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
    fun `decrement subtracts and floors at zero`() =
        runTest {
            db.inventoryDao().upsert(inventoryEntity(currentStock = 1.5))

            db.inventoryDao().decrement("m1", 1.0)
            assertThat(db.inventoryDao().getByMedication("m1")?.currentStock).isEqualTo(0.5)

            db.inventoryDao().decrement("m1", 5.0)
            assertThat(db.inventoryDao().getByMedication("m1")?.currentStock).isEqualTo(0.0)
        }

    @Test
    fun `decrement is a no-op when tracking disabled`() =
        runTest {
            db.inventoryDao().upsert(inventoryEntity(currentStock = 10.0, trackingEnabled = false))
            db.inventoryDao().decrement("m1", 1.0)
            assertThat(db.inventoryDao().getByMedication("m1")?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `refill adds and stamps time`() =
        runTest {
            db.inventoryDao().upsert(inventoryEntity(currentStock = 2.0))
            db.inventoryDao().refill("m1", 60.0, testInstant)

            val stored = db.inventoryDao().getByMedication("m1")
            assertThat(stored?.currentStock).isEqualTo(62.0)
            assertThat(stored?.lastRefillAt).isEqualTo(testInstant)
        }
}
