package icu.nd4y.dosette.data.db.entity

import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Package metadata is shared by all cabinets; balances have one authoritative stock row. */
@Entity(
    tableName = "medication_variants",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationId")],
)
data class MedicationVariantRecord(
    @PrimaryKey val id: String,
    val medicationId: String,
    val label: String?,
    val strengthValue: Double?,
    val strengthUnit: String?,
    val sortOrder: Int,
    val trackingEnabled: Boolean,
    val lowStockThreshold: Double?,
    val defaultRefillAmount: Double?,
)

internal const val VARIANT_COLUMNS =
    "v.id, v.medicationId, v.label, v.strengthValue, v.strengthUnit, " +
        "v.sortOrder, v.trackingEnabled, COALESCE(s.currentStock, 0.0) AS currentStock, " +
        "v.lowStockThreshold, v.defaultRefillAmount, s.lastRefillAt, "
internal const val VARIANT_INVENTORY_SQL =
    "SELECT " + VARIANT_COLUMNS +
        "COALESCE(cs.cabinetId, 'main:' || m.profileId) AS cabinetId, c.name AS cabinetName " +
        "FROM medication_variants v JOIN medications m ON m.id = v.medicationId " +
        "LEFT JOIN cabinet_selections cs ON cs.profileId = m.profileId " +
        "LEFT JOIN cabinets c ON c.id = COALESCE(cs.cabinetId, 'main:' || m.profileId) " +
        "LEFT JOIN cabinet_stocks s ON s.variantId = v.id AND " +
        "s.cabinetId = COALESCE(cs.cabinetId, 'main:' || m.profileId)"
internal const val VARIANT_AT_CABINET_SQL =
    "SELECT " + VARIANT_COLUMNS +
        "c.id AS cabinetId, c.name AS cabinetName FROM medication_variants v " +
        "JOIN medications m ON m.id = v.medicationId " +
        "JOIN cabinets c ON c.profileId = m.profileId AND c.id = :cabinetId " +
        "LEFT JOIN cabinet_stocks s ON s.variantId = v.id AND s.cabinetId = c.id "
internal const val VARIANT_MAIN_SQL =
    "SELECT " + VARIANT_COLUMNS +
        "'main:' || m.profileId AS cabinetId, c.name AS cabinetName FROM medication_variants v " +
        "JOIN medications m ON m.id = v.medicationId " +
        "LEFT JOIN cabinets c ON c.id = 'main:' || m.profileId " +
        "LEFT JOIN cabinet_stocks s ON s.variantId = v.id AND s.cabinetId = 'main:' || m.profileId"

/** Live projection of a shared package in its profile's selected cabinet. */
@DatabaseView(viewName = "variant_inventory", value = VARIANT_INVENTORY_SQL)
data class MedicationVariantEntity(
    val id: String,
    val medicationId: String,
    val label: String?,
    val strengthValue: Double?,
    val strengthUnit: String?,
    val sortOrder: Int,
    val trackingEnabled: Boolean,
    val currentStock: Double,
    val lowStockThreshold: Double?,
    val defaultRefillAmount: Double?,
    val lastRefillAt: Instant?,
    val cabinetId: String? = null,
    val cabinetName: String? = null,
)

internal fun MedicationVariantEntity.toRecord() =
    MedicationVariantRecord(
        id,
        medicationId,
        label,
        strengthValue,
        strengthUnit,
        sortOrder,
        trackingEnabled,
        lowStockThreshold,
        defaultRefillAmount,
    )
