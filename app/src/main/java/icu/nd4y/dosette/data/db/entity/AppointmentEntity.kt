package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "appointments",
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
data class AppointmentEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val title: String,
    val doctorName: String?,
    val location: String?,
    val date: LocalDate,
    val timeMinutes: Int,
    val notes: String?,
    /** Comma-separated minute offsets, e.g. "1440,120". */
    val reminderOffsetsMin: String,
    val createdAt: Instant,
)
