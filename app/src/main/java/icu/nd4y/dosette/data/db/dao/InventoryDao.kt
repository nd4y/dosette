package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.InventoryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory WHERE medicationId = :medicationId")
    suspend fun getByMedication(medicationId: String): InventoryEntity?

    @Query("SELECT * FROM inventory WHERE medicationId = :medicationId")
    fun observeByMedication(medicationId: String): Flow<InventoryEntity?>

    @Upsert
    suspend fun upsert(inventory: InventoryEntity)

    /** Atomic, floors at zero. */
    @Query(
        "UPDATE inventory SET currentStock = MAX(0, currentStock - :amount) " +
            "WHERE medicationId = :medicationId AND trackingEnabled = 1",
    )
    suspend fun decrement(
        medicationId: String,
        amount: Double,
    )

    /** Undo path: restores stock after a Take is reverted. */
    @Query(
        "UPDATE inventory SET currentStock = currentStock + :amount " +
            "WHERE medicationId = :medicationId AND trackingEnabled = 1",
    )
    suspend fun increment(
        medicationId: String,
        amount: Double,
    )

    @Query(
        "UPDATE inventory SET currentStock = currentStock + :amount, lastRefillAt = :at " +
            "WHERE medicationId = :medicationId",
    )
    suspend fun refill(
        medicationId: String,
        amount: Double,
        at: Instant,
    )
}
