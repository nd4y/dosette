package icu.nd4y.dosette.data.repository

import icu.nd4y.dosette.data.db.dao.InventoryDao
import icu.nd4y.dosette.data.db.dao.MedicationDao
import icu.nd4y.dosette.data.db.dao.ScheduleDao
import icu.nd4y.dosette.data.db.entity.MedicationWithDetails
import icu.nd4y.dosette.data.db.timeEntities
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.data.db.toEntity
import icu.nd4y.dosette.domain.model.Inventory
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Medication together with its schedule versions and stock. */
data class MedicationDetails(
    val medication: Medication,
    val schedules: List<Schedule>,
    val inventory: Inventory?,
) {
    /** Schedule versions active on [date]. */
    fun schedulesActiveOn(date: LocalDate): List<Schedule> =
        schedules.filter { !it.startDate.isAfter(date) && (it.endDate == null || !it.endDate.isBefore(date)) }
}

interface MedicationRepository {
    fun observeByProfile(profileId: String): Flow<List<MedicationDetails>>

    fun observeDetails(medicationId: String): Flow<MedicationDetails?>

    suspend fun getAllActive(): List<MedicationDetails>

    suspend fun upsert(medication: Medication)

    suspend fun addSchedule(schedule: Schedule)

    /** Schedule edit: closes [currentScheduleId] on [closeOn] and inserts [replacement]. */
    suspend fun replaceSchedule(
        currentScheduleId: String,
        closeOn: LocalDate,
        replacement: Schedule,
    )

    suspend fun upsertInventory(inventory: Inventory)

    suspend fun refill(
        medicationId: String,
        amount: Double,
        at: Instant,
    )

    suspend fun archive(
        medicationId: String,
        at: Instant,
    )

    suspend fun unarchive(medicationId: String)

    /** Destructive: cascades to schedules, dose logs and inventory. */
    suspend fun delete(medicationId: String)
}

@Singleton
class MedicationRepositoryImpl
    @Inject
    constructor(
        private val medicationDao: MedicationDao,
        private val scheduleDao: ScheduleDao,
        private val inventoryDao: InventoryDao,
    ) : MedicationRepository {
        override fun observeByProfile(profileId: String): Flow<List<MedicationDetails>> =
            medicationDao.observeByProfile(profileId).map { list -> list.map { it.toDetails() } }

        override fun observeDetails(medicationId: String): Flow<MedicationDetails?> =
            medicationDao.observeWithDetails(medicationId).map { it?.toDetails() }

        override suspend fun getAllActive(): List<MedicationDetails> =
            medicationDao.getAllActiveWithDetails().map { it.toDetails() }

        override suspend fun upsert(medication: Medication) = medicationDao.upsert(medication.toEntity())

        override suspend fun addSchedule(schedule: Schedule) =
            scheduleDao.insertWithTimes(schedule.toEntity(), schedule.timeEntities())

        override suspend fun replaceSchedule(
            currentScheduleId: String,
            closeOn: LocalDate,
            replacement: Schedule,
        ) = scheduleDao.replaceActive(currentScheduleId, closeOn, replacement.toEntity(), replacement.timeEntities())

        override suspend fun upsertInventory(inventory: Inventory) = inventoryDao.upsert(inventory.toEntity())

        override suspend fun refill(
            medicationId: String,
            amount: Double,
            at: Instant,
        ) = inventoryDao.refill(medicationId, amount, at)

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
                inventory = inventory?.toDomain(),
            )
    }
