package icu.nd4y.dosette.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ScheduleWithTimes(
    @Embedded val schedule: ScheduleEntity,
    @Relation(parentColumn = "id", entityColumn = "scheduleId")
    val times: List<ScheduleTimeEntity>,
)

data class MedicationWithDetails(
    @Embedded val medication: MedicationEntity,
    @Relation(entity = ScheduleEntity::class, parentColumn = "id", entityColumn = "medicationId")
    val schedules: List<ScheduleWithTimes>,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val inventory: InventoryEntity?,
)
