package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import icu.nd4y.dosette.data.db.entity.AppointmentEntity
import icu.nd4y.dosette.data.db.entity.DoseLogEntity
import icu.nd4y.dosette.data.db.entity.MedicationEntity
import icu.nd4y.dosette.data.db.entity.MedicationVariantEntity
import icu.nd4y.dosette.data.db.entity.ProfileEntity
import icu.nd4y.dosette.data.db.entity.ScheduleEntity
import icu.nd4y.dosette.data.db.entity.ScheduleTimeEntity

/** One import's worth of rows, in insert order (parents before children). */
data class BackupEntities(
    val profiles: List<ProfileEntity>,
    val medications: List<MedicationEntity>,
    val variants: List<MedicationVariantEntity>,
    val schedules: List<ScheduleEntity>,
    val scheduleTimes: List<ScheduleTimeEntity>,
    val doseLogs: List<DoseLogEntity>,
    val appointments: List<AppointmentEntity>,
)

/** Full-table access for backup export and the wipe+insert import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder")
    suspend fun profiles(): List<ProfileEntity>

    @Query("SELECT * FROM medications ORDER BY createdAt")
    suspend fun medications(): List<MedicationEntity>

    @Query("SELECT * FROM medication_variants ORDER BY sortOrder")
    suspend fun variants(): List<MedicationVariantEntity>

    @Query("SELECT * FROM schedules ORDER BY createdAt")
    suspend fun schedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedule_times ORDER BY sortIndex")
    suspend fun scheduleTimes(): List<ScheduleTimeEntity>

    @Query("SELECT * FROM dose_logs ORDER BY date, timeMinutes")
    suspend fun doseLogs(): List<DoseLogEntity>

    @Query("SELECT * FROM appointments ORDER BY date, timeMinutes")
    suspend fun appointments(): List<AppointmentEntity>

    @Query("DELETE FROM profiles")
    suspend fun wipeProfiles()

    @Query("DELETE FROM reminder_states")
    suspend fun wipeReminderStates()

    @Insert
    suspend fun insertProfiles(items: List<ProfileEntity>)

    @Insert
    suspend fun insertMedications(items: List<MedicationEntity>)

    @Insert
    suspend fun insertVariants(items: List<MedicationVariantEntity>)

    @Insert
    suspend fun insertSchedules(items: List<ScheduleEntity>)

    @Insert
    suspend fun insertScheduleTimes(items: List<ScheduleTimeEntity>)

    @Insert
    suspend fun insertDoseLogs(items: List<DoseLogEntity>)

    @Insert
    suspend fun insertAppointments(items: List<AppointmentEntity>)

    /**
     * Wipe + insert in one transaction: deleting profiles cascades through
     * medications to variants, schedules, times and logs; parents go back
     * in before children. Any failure rolls the whole import back.
     */
    @Transaction
    suspend fun replaceAll(entities: BackupEntities) {
        wipeProfiles()
        wipeReminderStates()
        insertProfiles(entities.profiles)
        insertMedications(entities.medications)
        insertVariants(entities.variants)
        insertSchedules(entities.schedules)
        insertScheduleTimes(entities.scheduleTimes)
        insertDoseLogs(entities.doseLogs)
        insertAppointments(entities.appointments)
    }
}
