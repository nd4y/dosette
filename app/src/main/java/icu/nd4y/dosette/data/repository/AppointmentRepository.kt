package icu.nd4y.dosette.data.repository

import icu.nd4y.dosette.data.db.dao.AppointmentDao
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.data.db.toEntity
import icu.nd4y.dosette.domain.model.Appointment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface AppointmentRepository {
    fun observeUpcoming(
        profileId: String,
        from: LocalDate,
    ): Flow<List<Appointment>>

    suspend fun getAllFrom(from: LocalDate): List<Appointment>

    suspend fun getById(id: String): Appointment?

    suspend fun upsert(appointment: Appointment)

    suspend fun delete(id: String)
}

@Singleton
class AppointmentRepositoryImpl
    @Inject
    constructor(
        private val appointmentDao: AppointmentDao,
    ) : AppointmentRepository {
        override fun observeUpcoming(
            profileId: String,
            from: LocalDate,
        ): Flow<List<Appointment>> =
            appointmentDao.observeUpcoming(profileId, from).map { list -> list.map { it.toDomain() } }

        override suspend fun getAllFrom(from: LocalDate): List<Appointment> =
            appointmentDao.getAllFrom(from).map { it.toDomain() }

        override suspend fun getById(id: String): Appointment? = appointmentDao.getById(id)?.toDomain()

        override suspend fun upsert(appointment: Appointment) = appointmentDao.upsert(appointment.toEntity())

        override suspend fun delete(id: String) = appointmentDao.delete(id)
    }
