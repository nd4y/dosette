package icu.nd4y.dosette.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MedicationVariantDaoTest {
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
    fun `variants come back ordered and independent`() =
        runTest {
            db.medicationVariantDao().upsert(variantEntity(id = "v75", strengthValue = 75.0, sortOrder = 1))
            db.medicationVariantDao().upsert(variantEntity(id = "v150", strengthValue = 150.0, sortOrder = 0))

            val variants = db.medicationVariantDao().getByMedication("m1")
            assertThat(variants.map { it.id }).containsExactly("v150", "v75").inOrder()
        }

    @Test
    fun `decrement targets one variant and floors at zero`() =
        runTest {
            db.medicationVariantDao().upsert(variantEntity(id = "v150", strengthValue = 150.0, currentStock = 10.0))
            db.medicationVariantDao().upsert(variantEntity(id = "v75", strengthValue = 75.0, currentStock = 4.0))

            // A 150 mg dose taken as 2 x 75 mg touches only the 75 mg pool.
            db.medicationVariantDao().decrement("v75", 2.0)

            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(2.0)
            assertThat(db.medicationVariantDao().getById("v150")?.currentStock).isEqualTo(10.0)

            db.medicationVariantDao().decrement("v75", 99.0)
            assertThat(db.medicationVariantDao().getById("v75")?.currentStock).isEqualTo(0.0)
        }

    @Test
    fun `decrement is a no-op when tracking disabled`() =
        runTest {
            db.medicationVariantDao().upsert(variantEntity(id = "v1", currentStock = 10.0, trackingEnabled = false))
            db.medicationVariantDao().decrement("v1", 1.0)
            assertThat(db.medicationVariantDao().getById("v1")?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `refill adds and stamps time`() =
        runTest {
            db.medicationVariantDao().upsert(variantEntity(id = "v1", currentStock = 2.0))
            db.medicationVariantDao().refill("v1", 60.0, testInstant)

            val stored = db.medicationVariantDao().getById("v1")
            assertThat(stored?.currentStock).isEqualTo(62.0)
            assertThat(stored?.lastRefillAt).isEqualTo(testInstant)
        }

    @Test
    fun `increment restores stock for undo`() =
        runTest {
            db.medicationVariantDao().upsert(variantEntity(id = "v1", currentStock = 2.0))
            db.medicationVariantDao().increment("v1", 2.0)
            assertThat(db.medicationVariantDao().getById("v1")?.currentStock).isEqualTo(4.0)
        }

    @Test
    fun `deleting the medication cascades to variants`() =
        runTest {
            db.medicationVariantDao().upsert(variantEntity(id = "v1"))
            db.medicationDao().delete("m1")
            assertThat(db.medicationVariantDao().getById("v1")).isNull()
        }
}
