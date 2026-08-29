package icu.nd4y.dosette.data.repository

import icu.nd4y.dosette.data.db.dao.DoseLogDao
import icu.nd4y.dosette.data.db.dao.MedicationVariantDao
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.data.db.toEntity
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class AdherenceCounts(
    val taken: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
) {
    val total: Int get() = taken + skipped + missed
}

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

    /** Idempotent by occurrence identity; returns true if the row was written. */
    suspend fun recordScheduledIfAbsent(log: DoseLog): Boolean

    /**
     * Authoritative write for a user action on a scheduled occurrence:
     * updates the existing row for this occurrence if one exists (e.g. a
     * retroactive flip of MISSED), inserts [log] otherwise.
     */
    suspend fun finalizeScheduled(log: DoseLog)

    suspend fun upsert(log: DoseLog)

    suspend fun recordPrn(log: DoseLog)

    suspend fun adherence(
        profileId: String,
        from: LocalDate,
        to: LocalDate,
    ): AdherenceCounts

    suspend fun adherenceForMedication(
        medicationId: String,
        from: LocalDate,
        to: LocalDate,
    ): AdherenceCounts

    suspend fun getById(id: String): DoseLog?

    suspend fun delete(id: String)
}

@Singleton
class DoseLogRepositoryImpl
    @Inject
    constructor(
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

        override suspend fun upsert(log: DoseLog) = doseLogDao.upsert(log.toEntity())

        override suspend fun recordPrn(log: DoseLog) {
            doseLogDao.insert(log.toEntity())
            if (log.status == DoseStatus.TAKEN && log.variantId != null && log.consumedUnits != null) {
                variantDao.decrement(log.variantId, log.consumedUnits)
            }
        }

        override suspend fun adherence(
            profileId: String,
            from: LocalDate,
            to: LocalDate,
        ): AdherenceCounts = doseLogDao.adherenceCounts(profileId, from, to).toAdherence()

        override suspend fun adherenceForMedication(
            medicationId: String,
            from: LocalDate,
            to: LocalDate,
        ): AdherenceCounts = doseLogDao.adherenceCountsForMedication(medicationId, from, to).toAdherence()

        override suspend fun getById(id: String): DoseLog? = doseLogDao.getById(id)?.toDomain()

        override suspend fun delete(id: String) = doseLogDao.delete(id)

        private fun List<icu.nd4y.dosette.data.db.dao.StatusCount>.toAdherence(): AdherenceCounts =
            AdherenceCounts(
                taken = firstOrNull { it.status == DoseStatus.TAKEN.name }?.count ?: 0,
                skipped = firstOrNull { it.status == DoseStatus.SKIPPED.name }?.count ?: 0,
                missed = firstOrNull { it.status == DoseStatus.MISSED.name }?.count ?: 0,
            )
    }
