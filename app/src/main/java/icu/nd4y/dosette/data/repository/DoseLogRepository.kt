package icu.nd4y.dosette.data.repository

import androidx.room.withTransaction
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.dao.DoseLogDao
import icu.nd4y.dosette.data.db.dao.MedicationVariantDao
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.data.db.toEntity
import icu.nd4y.dosette.data.db.toMinutes
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface DoseLogRepository {
    fun observeRange(
        profileId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DoseLog>>

    suspend fun getScheduledInRange(
        from: LocalDate,
        to: LocalDate,
    ): List<DoseLog>

    /** The scheduled log for one occurrence, if any. */
    suspend fun getScheduled(key: OccurrenceKey): DoseLog?

    /** Idempotent by occurrence identity; returns true if the row was written. */
    suspend fun recordScheduledIfAbsent(log: DoseLog): Boolean

    /**
     * Authoritative write for a user action on a scheduled occurrence:
     * updates the existing row for this occurrence if one exists (e.g. a
     * retroactive flip of MISSED), inserts [log] otherwise.
     */
    suspend fun finalizeScheduled(log: DoseLog)

    suspend fun recordPrn(log: DoseLog)

    /** Undo of [recordPrn]: deletes the log and returns the consumed stock. */
    suspend fun undoPrn(logId: String)

    suspend fun getById(id: String): DoseLog?

    suspend fun delete(id: String)
}

@Singleton
class DoseLogRepositoryImpl
    @Inject
    constructor(
        private val db: AppDatabase,
        private val doseLogDao: DoseLogDao,
        private val variantDao: MedicationVariantDao,
    ) : DoseLogRepository {
        override fun observeRange(
            profileId: String,
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<DoseLog>> =
            doseLogDao.observeByProfileAndDateRange(profileId, from, to).map { list -> list.map { it.toDomain() } }

        override suspend fun getScheduledInRange(
            from: LocalDate,
            to: LocalDate,
        ): List<DoseLog> = doseLogDao.getScheduledInRange(from, to).map { it.toDomain() }

        override suspend fun getScheduled(key: OccurrenceKey): DoseLog? =
            doseLogDao.getScheduled(key.medicationId, key.date, key.time.toMinutes())?.toDomain()

        override suspend fun recordScheduledIfAbsent(log: DoseLog): Boolean =
            doseLogDao.insertScheduledIfAbsent(log.toEntity())

        override suspend fun finalizeScheduled(log: DoseLog) {
            val entity = log.toEntity()
            val timeMinutes = requireNotNull(entity.timeMinutes) { "finalizeScheduled needs a planned time" }
            val existing = doseLogDao.getScheduled(entity.medicationId, entity.date, timeMinutes)
            if (existing == null) {
                doseLogDao.insert(entity)
            } else {
                doseLogDao.upsert(
                    existing.copy(
                        status = entity.status,
                        actedAt = entity.actedAt,
                        variantId = entity.variantId,
                        consumedUnits = entity.consumedUnits,
                        updatedAt = entity.updatedAt,
                    ),
                )
            }
        }

        // Log and stock move together: process death between the two writes
        // would leave a recorded intake without the decrement (or a returned
        // stock with the log still present, ready for a second undo).
        override suspend fun recordPrn(log: DoseLog) =
            db.withTransaction {
                doseLogDao.insert(log.toEntity())
                if (log.status == DoseStatus.TAKEN && log.variantId != null && log.consumedUnits != null) {
                    variantDao.decrement(log.variantId, log.consumedUnits)
                }
            }

        override suspend fun undoPrn(logId: String) =
            db.withTransaction {
                val log = getById(logId) ?: return@withTransaction
                if (log.kind != DoseKind.PRN) return@withTransaction
                if (log.status == DoseStatus.TAKEN && log.variantId != null && log.consumedUnits != null) {
                    variantDao.increment(log.variantId, log.consumedUnits)
                }
                doseLogDao.delete(logId)
            }

        override suspend fun getById(id: String): DoseLog? = doseLogDao.getById(id)?.toDomain()

        override suspend fun delete(id: String) = doseLogDao.delete(id)
    }
