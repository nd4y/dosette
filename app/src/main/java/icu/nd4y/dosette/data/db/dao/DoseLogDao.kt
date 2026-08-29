package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.DoseLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class StatusCount(
    val status: String,
    val count: Int,
)

@Dao
interface DoseLogDao {
    @Query(
        "SELECT * FROM dose_logs WHERE profileId = :profileId AND date BETWEEN :from AND :to " +
            "ORDER BY date, timeMinutes",
    )
    fun observeByProfileAndDateRange(
        profileId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DoseLogEntity>>

    @Query(
        "SELECT * FROM dose_logs WHERE medicationId = :medicationId AND date = :date " +
            "AND timeMinutes = :timeMinutes AND kind = 'SCHEDULED'",
    )
    suspend fun getScheduled(
        medicationId: String,
        date: LocalDate,
        timeMinutes: Int,
    ): DoseLogEntity?

    @Query(
        "SELECT * FROM dose_logs WHERE date BETWEEN :from AND :to AND kind = 'SCHEDULED'",
    )
    suspend fun getScheduledInRange(
        from: LocalDate,
        to: LocalDate,
    ): List<DoseLogEntity>

    @Insert
    suspend fun insert(log: DoseLogEntity)

    @Upsert
    suspend fun upsert(log: DoseLogEntity)

    /**
     * Idempotent write keyed by wall-clock occurrence identity
     * (medicationId, date, timeMinutes). Returns true if the row was inserted.
     * A partial unique index can not express "unique only where kind =
     * SCHEDULED", hence the transaction.
     */
    @Transaction
    suspend fun insertScheduledIfAbsent(log: DoseLogEntity): Boolean {
        require(log.kind == "SCHEDULED" && log.timeMinutes != null) {
            "insertScheduledIfAbsent is for scheduled doses only"
        }
        val existing = getScheduled(log.medicationId, log.date, log.timeMinutes)
        if (existing != null) return false
        insert(log)
        return true
    }

    @Query(
        "SELECT status, COUNT(*) as count FROM dose_logs WHERE profileId = :profileId " +
            "AND kind = 'SCHEDULED' AND date BETWEEN :from AND :to GROUP BY status",
    )
    suspend fun adherenceCounts(
        profileId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<StatusCount>

    @Query(
        "SELECT status, COUNT(*) as count FROM dose_logs WHERE medicationId = :medicationId " +
            "AND kind = 'SCHEDULED' AND date BETWEEN :from AND :to GROUP BY status",
    )
    suspend fun adherenceCountsForMedication(
        medicationId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<StatusCount>

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun getById(id: String): DoseLogEntity?

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun delete(id: String)
}
