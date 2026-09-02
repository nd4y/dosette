package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.MedicationVariantEntity
import java.time.Instant

@Dao
interface MedicationVariantDao {
    @Query("SELECT * FROM medication_variants WHERE medicationId = :medicationId ORDER BY sortOrder")
    suspend fun getByMedication(medicationId: String): List<MedicationVariantEntity>

    @Query("SELECT * FROM medication_variants WHERE id = :id")
    suspend fun getById(id: String): MedicationVariantEntity?

    @Upsert
    suspend fun upsert(variant: MedicationVariantEntity)

    /** Atomic, floors at zero; no-op when tracking is disabled. */
    @Query(
        "UPDATE medication_variants SET currentStock = MAX(0, currentStock - :units) " +
            "WHERE id = :id AND trackingEnabled = 1",
    )
    suspend fun decrement(
        id: String,
        units: Double,
    )

    /** Undo path: restores stock after a Take is reverted. */
    @Query(
        "UPDATE medication_variants SET currentStock = currentStock + :units " +
            "WHERE id = :id AND trackingEnabled = 1",
    )
    suspend fun increment(
        id: String,
        units: Double,
    )

    @Query(
        "UPDATE medication_variants SET currentStock = currentStock + :units, lastRefillAt = :at " +
            "WHERE id = :id",
    )
    suspend fun refill(
        id: String,
        units: Double,
        at: Instant,
    )

    @Query("DELETE FROM medication_variants WHERE id = :id")
    suspend fun delete(id: String)

    /** Correction path: one UPDATE cannot clobber a decrement racing it. */
    @Query("UPDATE medication_variants SET currentStock = :units WHERE id = :id")
    suspend fun setStock(
        id: String,
        units: Double,
    )

    /**
     * [decrement] that reports the stock before and after inside one
     * transaction; null when the variant is untracked or gone.
     */
    @Transaction
    suspend fun consume(
        id: String,
        units: Double,
    ): StockChange? {
        val before = getById(id)?.takeIf { it.trackingEnabled }
        if (before != null) decrement(id, units)
        val after = before?.let { getById(id) }
        return if (before == null || after == null) null else StockChange(before.currentStock, after.currentStock)
    }
}

/** Stock of a variant around one decrement. */
data class StockChange(
    val before: Double,
    val after: Double,
)
