package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profileId"),
        Index("medicationId", "date"),
        Index("profileId", "date"),
    ],
)
data class DoseLogEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val medicationId: String,
    val scheduleId: String?,
    val kind: String,
    val date: LocalDate,
    /** Planned wall-clock time in minutes of day; null for PRN. */
    val timeMinutes: Int?,
    val scheduledAt: Instant?,
    val status: String,
    val actedAt: Instant?,
    val amount: Double,
    val note: String?,
    val updatedAt: Instant,
)
