package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import icu.nd4y.dosette.data.db.entity.AppointmentEntity
import icu.nd4y.dosette.data.db.entity.CabinetEntity
import icu.nd4y.dosette.data.db.entity.CabinetSelectionEntity
import icu.nd4y.dosette.data.db.entity.CabinetStockEntity
import icu.nd4y.dosette.data.db.entity.DoseLogEntity
import icu.nd4y.dosette.data.db.entity.MedicationEntity
import icu.nd4y.dosette.data.db.entity.MedicationVariantEntity
import icu.nd4y.dosette.data.db.entity.MedicationVariantRecord
import icu.nd4y.dosette.data.db.entity.ProfileEntity
import icu.nd4y.dosette.data.db.entity.ScheduleEntity
import icu.nd4y.dosette.data.db.entity.ScheduleTimeEntity
import icu.nd4y.dosette.data.db.entity.VARIANT_MAIN_SQL
import icu.nd4y.dosette.data.db.entity.toRecord

/** One import's worth of rows, in insert order (parents before children). */
data class BackupEntities(
    val profiles: List<ProfileEntity>,
    val medications: List<MedicationEntity>,
    val variants: List<MedicationVariantEntity>,
    val schedules: List<ScheduleEntity>,
    val scheduleTimes: List<ScheduleTimeEntity>,
    val doseLogs: List<DoseLogEntity>,
    val appointments: List<AppointmentEntity>,
    val cabinets: List<CabinetEntity> = emptyList(),
    val stocks: List<CabinetStockEntity> = emptyList(),
    val selections: List<CabinetSelectionEntity> = emptyList(),
)

/** Full-table access for backup export and the wipe+insert import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder")
    suspend fun profiles(): List<ProfileEntity>

    @Query("SELECT * FROM medications ORDER BY createdAt")
    suspend fun medications(): List<MedicationEntity>

    @Query(VARIANT_MAIN_SQL + " ORDER BY v.sortOrder")
    suspend fun variants(): List<MedicationVariantEntity>

    @Query("SELECT * FROM schedules ORDER BY createdAt")
    suspend fun schedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedule_times ORDER BY sortIndex")
    suspend fun scheduleTimes(): List<ScheduleTimeEntity>

    @Query("SELECT * FROM dose_logs ORDER BY date, timeMinutes")
    suspend fun doseLogs(): List<DoseLogEntity>

    @Query("SELECT * FROM appointments ORDER BY date, timeMinutes")
    suspend fun appointments(): List<AppointmentEntity>

    @Query("SELECT * FROM cabinets ORDER BY profileId, sortOrder, id")
    suspend fun cabinets(): List<CabinetEntity>

    @Query("SELECT * FROM cabinet_stocks ORDER BY cabinetId, variantId")
    suspend fun stocks(): List<CabinetStockEntity>

    @Query("SELECT * FROM cabinet_selections")
    suspend fun selections(): List<CabinetSelectionEntity>

    /** A consistent view even if a medication, profile or dose changes while the export runs. */
    @Transaction
    suspend fun snapshot(): BackupEntities =
        BackupEntities(
            profiles = profiles(),
            medications = medications(),
            variants = variants(),
            schedules = schedules(),
            scheduleTimes = scheduleTimes(),
            doseLogs = doseLogs(),
            appointments = appointments(),
            cabinets = cabinets(),
            stocks = stocks(),
            selections = selections(),
        )

    @Query("DELETE FROM profiles")
    suspend fun wipeProfiles()

    @Query("DELETE FROM reminder_states")
    suspend fun wipeReminderStates()

    @Insert
    suspend fun insertProfiles(items: List<ProfileEntity>)

    @Insert
    suspend fun insertMedications(items: List<MedicationEntity>)

    @Insert
    suspend fun insertVariantRecords(items: List<MedicationVariantRecord>)

    @Insert
    suspend fun insertCabinets(items: List<CabinetEntity>)

    @Insert
    suspend fun insertStocks(items: List<CabinetStockEntity>)

    @Insert
    suspend fun insertSelections(items: List<CabinetSelectionEntity>)

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
        insertVariantRecords(entities.variants.map { it.toRecord() })
        val cabinets =
            entities.cabinets.ifEmpty {
                entities.profiles.map { CabinetEntity("main:${it.id}", it.id, null, 0, it.createdAt) }
            }
        insertCabinets(cabinets)
        insertSelections(
            entities.selections.ifEmpty {
                entities.profiles.map { CabinetSelectionEntity(it.id, "main:${it.id}") }
            },
        )
        insertStocks(
            if (entities.cabinets.isEmpty()) {
                val profileByMed = entities.medications.associate { it.id to it.profileId }
                entities.variants.map {
                    CabinetStockEntity(
                        it.id,
                        "main:${profileByMed.getValue(it.medicationId)}",
                        it.currentStock,
                        it.lastRefillAt,
                    )
                }
            } else {
                entities.stocks
            },
        )
        insertSchedules(entities.schedules)
        insertScheduleTimes(entities.scheduleTimes)
        insertDoseLogs(entities.doseLogs)
        insertAppointments(entities.appointments)
    }
}
