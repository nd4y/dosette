package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "schedules",
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
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val type: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    /** Rhythm anchor for every-N / cycle versions, see the domain Schedule; null = startDate. Added in v3. */
    val anchorDate: LocalDate?,
    /** Single-day dose added from the calendar (v3; backfilled from startDate == endDate). */
    val oneOff: Boolean,
    /** Bitmask, bit 0 = Monday. */
    val weekdaysMask: Int,
    val intervalDays: Int?,
    val cycleDaysOn: Int?,
    val cycleDaysOff: Int?,
    val defaultDoseAmount: Double,
    val remindersEnabled: Boolean,
    val createdAt: Instant,
)
