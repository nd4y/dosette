package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.CabinetStockEntity
import icu.nd4y.dosette.data.db.entity.MedicationVariantEntity
import icu.nd4y.dosette.data.db.entity.MedicationVariantRecord
import icu.nd4y.dosette.data.db.entity.VARIANT_AT_CABINET_SQL
import icu.nd4y.dosette.data.db.entity.toRecord
import java.time.Instant

@Dao
interface MedicationVariantDao {
    @Query("SELECT * FROM variant_inventory WHERE medicationId = :medicationId ORDER BY sortOrder")
    suspend fun getByMedication(medicationId: String): List<MedicationVariantEntity>

    @Query(VARIANT_AT_CABINET_SQL + " WHERE v.medicationId = :medicationId ORDER BY v.sortOrder")
    suspend fun getByMedicationAt(
        medicationId: String,
        cabinetId: String,
    ): List<MedicationVariantEntity>

    @Query("SELECT * FROM variant_inventory WHERE id = :id")
    suspend fun getById(id: String): MedicationVariantEntity?

    @Query(VARIANT_AT_CABINET_SQL + " WHERE v.id = :id")
    suspend fun getAt(
        id: String,
        cabinetId: String,
    ): MedicationVariantEntity?

    @Upsert suspend fun upsertRecord(variant: MedicationVariantRecord)

    @Upsert suspend fun upsertStock(stock: CabinetStockEntity)

    @Query(
        "INSERT OR IGNORE INTO cabinets (id, profileId, name, sortOrder, createdAt, archivedAt) " +
            "SELECT 'main:' || p.id, p.id, NULL, 0, p.createdAt, NULL FROM profiles p " +
            "JOIN medications m ON m.profileId = p.id WHERE m.id = :medicationId",
    )
    suspend fun ensureCabinet(medicationId: String)

    @Query(
        "INSERT OR IGNORE INTO cabinet_selections (profileId, cabinetId) " +
            "SELECT profileId, 'main:' || profileId FROM medications WHERE id = :medicationId",
    )
    suspend fun ensureSelection(medicationId: String)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM cabinets c JOIN medications m ON c.profileId = m.profileId " +
            "WHERE c.id = :cabinetId AND m.id = :medicationId)",
    )
    suspend fun cabinetMatches(
        medicationId: String,
        cabinetId: String,
    ): Boolean

    @Transaction suspend fun upsert(variant: MedicationVariantEntity) {
        require(variant.currentStock.isFinite() && variant.currentStock >= 0)
        ensureCabinet(variant.medicationId)
        ensureSelection(variant.medicationId)
        upsertRecord(variant.toRecord())
        val id = requireNotNull(variant.cabinetId ?: getById(variant.id)?.cabinetId)
        require(cabinetMatches(variant.medicationId, id))
        upsertStock(CabinetStockEntity(variant.id, id, variant.currentStock, variant.lastRefillAt))
    }

    @Transaction suspend fun upsertFromEditor(
        variant: MedicationVariantEntity,
        updateStock: Boolean,
    ) {
        ensureCabinet(variant.medicationId)
        ensureSelection(variant.medicationId)
        val current = variant.cabinetId?.let { getAt(variant.id, it) } ?: getById(variant.id)
        upsert(
            variant.copy(
                currentStock =
                    if (updateStock) {
                        variant.currentStock
                    } else {
                        current?.currentStock
                            ?: variant.currentStock
                    },
                lastRefillAt = current?.lastRefillAt ?: variant.lastRefillAt,
            ),
        )
    }

    @Query(
        "UPDATE cabinet_stocks SET currentStock = MAX(0, currentStock - :units) " +
            "WHERE variantId = :id AND cabinetId = :cabinetId AND EXISTS " +
            "(SELECT 1 FROM medication_variants WHERE id = :id AND trackingEnabled = 1)",
    )
    suspend fun debit(
        id: String,
        units: Double,
        cabinetId: String,
    )

    @Query(
        "UPDATE cabinet_stocks SET currentStock = currentStock + :units " +
            "WHERE variantId = :id AND cabinetId = :cabinetId",
    )
    suspend fun credit(
        id: String,
        units: Double,
        cabinetId: String,
    )

    @Query("UPDATE cabinets SET archivedAt = NULL WHERE id = :cabinetId")
    suspend fun restoreCabinet(cabinetId: String)

    @Query("SELECT * FROM cabinet_stocks WHERE variantId = :id AND cabinetId = :cabinetId")
    suspend fun stock(
        id: String,
        cabinetId: String,
    ): CabinetStockEntity?

    @Transaction suspend fun selected(
        id: String,
        cabinetId: String?,
    ): MedicationVariantEntity? {
        val row = if (cabinetId == null) getById(id) else getAt(id, cabinetId)
        val source = row?.cabinetId ?: return null
        if (stock(id, source) == null) upsertStock(CabinetStockEntity(id, source, 0.0))
        return row
    }

    @Transaction suspend fun decrement(
        id: String,
        units: Double,
        cabinetId: String? = null,
    ) {
        require(units.isFinite() && units >= 0)
        val row = selected(id, cabinetId) ?: return
        debit(id, units, requireNotNull(row.cabinetId))
    }

    @Transaction suspend fun increment(
        id: String,
        units: Double,
        cabinetId: String? = null,
    ) {
        require(units.isFinite() && units >= 0)
        val row = selected(id, cabinetId) ?: return
        credit(id, units, requireNotNull(row.cabinetId))
        if (units > 0) restoreCabinet(row.cabinetId)
    }

    @Transaction suspend fun setStock(
        id: String,
        units: Double,
        cabinetId: String? = null,
    ) {
        require(units.isFinite() && units >= 0)
        val row = selected(id, cabinetId) ?: return
        upsertStock(CabinetStockEntity(id, requireNotNull(row.cabinetId), units, row.lastRefillAt))
    }

    @Transaction suspend fun refill(
        id: String,
        units: Double,
        at: Instant,
        cabinetId: String? = null,
    ) {
        require(units.isFinite() && units >= 0)
        val row = selected(id, cabinetId) ?: return
        upsertStock(CabinetStockEntity(id, requireNotNull(row.cabinetId), row.currentStock + units, at))
        if (units > 0) restoreCabinet(row.cabinetId)
    }

    @Query(
        "SELECT EXISTS(SELECT 1 FROM cabinet_stocks WHERE variantId = :id " +
            "AND cabinetId != :cabinetId AND currentStock > 0)",
    )
    suspend fun hasOtherStock(
        id: String,
        cabinetId: String,
    ): Boolean

    @Query("DELETE FROM medication_variants WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction suspend fun consume(
        id: String,
        units: Double,
        cabinetId: String? = null,
    ): StockChange? {
        require(units.isFinite() && units >= 0)
        val before = selected(id, cabinetId)?.takeIf { it.trackingEnabled } ?: return null
        debit(id, units, requireNotNull(before.cabinetId))
        val after = requireNotNull(getAt(id, before.cabinetId))
        return StockChange(before.currentStock, after.currentStock, before.cabinetId)
    }
}

data class StockChange(
    val before: Double,
    val after: Double,
    val cabinetId: String? = null,
)
