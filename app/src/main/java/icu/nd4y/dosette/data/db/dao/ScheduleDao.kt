package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import icu.nd4y.dosette.data.db.entity.ScheduleEntity
import icu.nd4y.dosette.data.db.entity.ScheduleTimeEntity
import icu.nd4y.dosette.data.db.entity.ScheduleWithTimes
import java.time.LocalDate

@Dao
interface ScheduleDao {
    @Transaction
    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId ORDER BY createdAt")
    suspend fun getByMedication(medicationId: String): List<ScheduleWithTimes>

    @Transaction
    @Query(
        "SELECT * FROM schedules WHERE startDate <= :date AND (endDate IS NULL OR endDate >= :date)",
    )
    suspend fun getActiveOn(date: LocalDate): List<ScheduleWithTimes>

    @Insert
    suspend fun insert(schedule: ScheduleEntity)

    @Insert
    suspend fun insertTimes(times: List<ScheduleTimeEntity>)

    @Query("UPDATE schedules SET endDate = :endDate WHERE id = :id")
    suspend fun closeSchedule(
        id: String,
        endDate: LocalDate,
    )

    /**
     * Schedule edit = close the current version and insert the new one.
     * One transaction so an interrupted edit can not lose the schedule.
     */
    @Transaction
    suspend fun replaceActive(
        currentId: String,
        closeOn: LocalDate,
        replacement: ScheduleEntity,
        replacementTimes: List<ScheduleTimeEntity>,
    ) {
        closeSchedule(currentId, closeOn)
        insert(replacement)
        insertTimes(replacementTimes)
    }

    @Transaction
    suspend fun insertWithTimes(
        schedule: ScheduleEntity,
        times: List<ScheduleTimeEntity>,
    ) {
        insert(schedule)
        insertTimes(times)
    }

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: String)
}
