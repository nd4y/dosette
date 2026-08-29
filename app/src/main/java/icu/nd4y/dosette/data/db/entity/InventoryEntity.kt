package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "inventory",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class InventoryEntity(
    @PrimaryKey val medicationId: String,
    val trackingEnabled: Boolean,
    val currentStock: Double,
    val lowStockThreshold: Double?,
    val defaultRefillAmount: Double?,
    val lastRefillAt: Instant?,
)
