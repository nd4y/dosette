package icu.nd4y.dosette.data.repository

import icu.nd4y.dosette.data.db.dao.MedicationDao
import icu.nd4y.dosette.data.db.dao.MedicationVariantDao
import icu.nd4y.dosette.data.db.dao.ScheduleDao
import icu.nd4y.dosette.data.db.dao.StockChange
import icu.nd4y.dosette.data.db.entity.MedicationWithDetails
import icu.nd4y.dosette.data.db.timeEntities
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.data.db.toEntity
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationVariant
import icu.nd4y.dosette.domain.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Medication together with its schedule versions and package variants. */
data class MedicationDetails(
    val medication: Medication,
    val schedules: List<Schedule>,
    val variants: List<MedicationVariant>,
) {
    /** Variant consumed by default on Take. */
    val defaultVariant: MedicationVariant?
        get() =
            variants.firstOrNull { it.id == medication.defaultVariantId }
                ?: variants.minByOrNull { it.sortOrder }

    /** Schedule versions active on [date]. */
    fun schedulesActiveOn(date: LocalDate): List<Schedule> =
        schedules.filter { !it.startDate.isAfter(date) && (it.endDate == null || !it.endDate.isBefore(date)) }
}

interface MedicationRepository {
    fun observeByProfile(profileId: String): Flow<List<MedicationDetails>>

    fun observeDetails(medicationId: String): Flow<MedicationDetails?>

    suspend fun getAllActive(): List<MedicationDetails>

    /** Archived ones included. */
    suspend fun getAll(): List<MedicationDetails>

    suspend fun getDetails(
        medicationId: String,
        cabinetId: String? = null,
    ): MedicationDetails?

    suspend fun upsert(medication: Medication)

    suspend fun addSchedule(schedule: Schedule)

    /** Schedule edit: closes [currentScheduleId] on [closeOn] and inserts [replacement]. */
    suspend fun replaceSchedule(
        currentScheduleId: String,
        closeOn: LocalDate,
        replacement: Schedule,
    )

    /** Removes the schedule version outright — one-off doses only. */
    suspend fun deleteSchedule(scheduleId: String)

    /** Ends the regimen: closes [scheduleId] on [closeOn] with nothing in its place. */
    suspend fun closeSchedule(
        scheduleId: String,
        closeOn: LocalDate,
    )

    suspend fun upsertVariant(variant: MedicationVariant)

    /** Save editor fields while preserving inventory the user did not explicitly change. */
    suspend fun upsertVariantFromEditor(
        variant: MedicationVariant,
        updateStock: Boolean,
    )

    suspend fun getVariant(
        variantId: String,
        cabinetId: String? = null,
    ): MedicationVariant?

    suspend fun hasOtherCabinetStock(
        variantId: String,
        cabinetId: String,
    ): Boolean

    suspend fun deleteVariant(variantId: String)

    /** Atomic, floors at zero, no-op when tracking is disabled. Units are units of the variant. */
    suspend fun decrementStock(
        variantId: String,
        units: Double,
        cabinetId: String? = null,
    )

    /** Undo path: restores the variant's stock. */
    suspend fun incrementStock(
        variantId: String,
        units: Double,
        cabinetId: String? = null,
    )

    /** Correction path: overwrites the counter with a recount, atomically. */
    suspend fun setStock(
        variantId: String,
        units: Double,
        cabinetId: String? = null,
    )

    /** Atomic decrement reporting the stock before and after; null when untracked or gone. */
    suspend fun consumeStock(
        variantId: String,
        units: Double,
        cabinetId: String? = null,
    ): StockChange?

    suspend fun refill(
        variantId: String,
        units: Double,
        at: Instant,
        cabinetId: String? = null,
    )

    suspend fun archive(
        medicationId: String,
        at: Instant,
    )

    suspend fun unarchive(medicationId: String)

    /** Destructive: cascades to schedules, dose logs and variants. */
    suspend fun delete(medicationId: String)
}

@Singleton
class MedicationRepositoryImpl
    @Inject
    constructor(
        private val medicationDao: MedicationDao,
        private val scheduleDao: ScheduleDao,
        private val variantDao: MedicationVariantDao,
    ) : MedicationRepository {
        override fun observeByProfile(profileId: String): Flow<List<MedicationDetails>> =
            medicationDao.observeByProfile(profileId).map { list -> list.map { it.toDetails() } }

        override fun observeDetails(medicationId: String): Flow<MedicationDetails?> =
            medicationDao.observeWithDetails(medicationId).map { it?.toDetails() }

        override suspend fun getAllActive(): List<MedicationDetails> =
            medicationDao.getAllActiveWithDetails().map { it.toDetails() }

        override suspend fun getAll(): List<MedicationDetails> =
            medicationDao.getAllWithDetails().map { it.toDetails() }

        override suspend fun getDetails(
            medicationId: String,
            cabinetId: String?,
        ): MedicationDetails? {
            val medication = medicationDao.getById(medicationId) ?: return null
            return if (cabinetId != null &&
                !variantDao.cabinetMatches(medicationId, cabinetId)
            ) {
                null
            } else {
                MedicationDetails(
                    medication = medication.toDomain(),
                    schedules = scheduleDao.getByMedication(medicationId).map { it.toDomain() },
                    variants =
                        (
                            if (cabinetId == null) {
                                variantDao.getByMedication(medicationId)
                            } else {
                                variantDao.getByMedicationAt(medicationId, cabinetId)
                            }
                        ).map { it.toDomain() },
                )
            }
        }

        override suspend fun upsert(medication: Medication) = medicationDao.upsert(medication.toEntity())

        override suspend fun addSchedule(schedule: Schedule) =
            scheduleDao.insertWithTimes(schedule.toEntity(), schedule.timeEntities())

        override suspend fun replaceSchedule(
            currentScheduleId: String,
            closeOn: LocalDate,
            replacement: Schedule,
        ) = scheduleDao.replaceActive(
            currentScheduleId,
            closeOn,
            replacement.toEntity(),
            replacement.timeEntities(),
        )

        override suspend fun deleteSchedule(scheduleId: String) = scheduleDao.delete(scheduleId)

        override suspend fun closeSchedule(
            scheduleId: String,
            closeOn: LocalDate,
        ) = scheduleDao.closeSchedule(scheduleId, closeOn)

        override suspend fun upsertVariant(variant: MedicationVariant) = variantDao.upsert(variant.toEntity())

        override suspend fun upsertVariantFromEditor(
            variant: MedicationVariant,
            updateStock: Boolean,
        ) = variantDao.upsertFromEditor(variant.toEntity(), updateStock)

        override suspend fun getVariant(
            variantId: String,
            cabinetId: String?,
        ): MedicationVariant? =
            (
                if (cabinetId ==
                    null
                ) {
                    variantDao.getById(variantId)
                } else {
                    variantDao.getAt(variantId, cabinetId)
                }
            )?.toDomain()

        override suspend fun hasOtherCabinetStock(
            variantId: String,
            cabinetId: String,
        ): Boolean = variantDao.hasOtherStock(variantId, cabinetId)

        override suspend fun deleteVariant(variantId: String) = variantDao.delete(variantId)

        override suspend fun decrementStock(
            variantId: String,
            units: Double,
            cabinetId: String?,
        ) = variantDao.decrement(variantId, units, cabinetId)

        override suspend fun incrementStock(
            variantId: String,
            units: Double,
            cabinetId: String?,
        ) = variantDao.increment(variantId, units, cabinetId)

        override suspend fun setStock(
            variantId: String,
            units: Double,
            cabinetId: String?,
        ) = variantDao.setStock(variantId, units, cabinetId)

        override suspend fun consumeStock(
            variantId: String,
            units: Double,
            cabinetId: String?,
        ): StockChange? = variantDao.consume(variantId, units, cabinetId)

        override suspend fun refill(
            variantId: String,
            units: Double,
            at: Instant,
            cabinetId: String?,
        ) = variantDao.refill(variantId, units, at, cabinetId)

        override suspend fun archive(
            medicationId: String,
            at: Instant,
        ) = medicationDao.archive(medicationId, at)

        override suspend fun unarchive(medicationId: String) = medicationDao.unarchive(medicationId)

        override suspend fun delete(medicationId: String) = medicationDao.delete(medicationId)

        private fun MedicationWithDetails.toDetails(): MedicationDetails =
            MedicationDetails(
                medication = medication.toDomain(),
                schedules = schedules.map { it.toDomain() },
                variants = variants.map { it.toDomain() },
            )
    }
