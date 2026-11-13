package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "cabinets",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class CabinetEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    /** Null is the localized default cabinet name. */
    val name: String?,
    val sortOrder: Int,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
)

@Entity(
    tableName = "cabinet_selections",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CabinetEntity::class,
            parentColumns = ["id"],
            childColumns = ["cabinetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cabinetId")],
)
data class CabinetSelectionEntity(
    @PrimaryKey val profileId: String,
    val cabinetId: String,
)

@Entity(
    tableName = "cabinet_stocks",
    primaryKeys = ["variantId", "cabinetId"],
    foreignKeys = [
        ForeignKey(
            entity = MedicationVariantRecord::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CabinetEntity::class,
            parentColumns = ["id"],
            childColumns = ["cabinetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("variantId"), Index("cabinetId")],
)
data class CabinetStockEntity(
    val variantId: String,
    val cabinetId: String,
    val currentStock: Double,
    val lastRefillAt: Instant? = null,
)
