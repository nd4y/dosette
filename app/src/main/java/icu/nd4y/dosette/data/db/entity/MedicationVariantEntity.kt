package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One package form of a medication ("150 mg capsules", "75 mg capsules")
 * together with its own stock. Every medication has at least one variant;
 * a 150 mg dose can then be consumed as 1x150 or 2x75 with per-variant
 * stock accounting.
 */
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
data class MedicationVariantEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    /** Optional display label; null = derived from strength. */
    val label: String?,
    /** Strength of one unit of this variant; null = generic unit (1 pc). */
    val strengthValue: Double?,
    val strengthUnit: String?,
    val sortOrder: Int,
    val trackingEnabled: Boolean,
    /** Units of this variant on hand; Double — half pills are real. */
    val currentStock: Double,
    val lowStockThreshold: Double?,
    val defaultRefillAmount: Double?,
    val lastRefillAt: Instant?,
)
