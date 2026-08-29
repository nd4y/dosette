package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_times",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduleId")],
)
data class ScheduleTimeEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    /** Minutes of day, wall clock. */
    val timeMinutes: Int,
    val doseAmount: Double,
    val sortIndex: Int,
)
