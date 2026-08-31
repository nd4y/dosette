package icu.nd4y.dosette.data.repository

import icu.nd4y.dosette.data.db.dao.ReminderStateDao
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.data.db.toEntity
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.ReminderState
import javax.inject.Inject
import javax.inject.Singleton

interface ReminderStateRepository {
    suspend fun getAll(): List<ReminderState>

    suspend fun get(key: OccurrenceKey): ReminderState?

    suspend fun upsert(state: ReminderState)

    suspend fun delete(key: OccurrenceKey)
}

@Singleton
class ReminderStateRepositoryImpl
    @Inject
    constructor(
        private val reminderStateDao: ReminderStateDao,
    ) : ReminderStateRepository {
        override suspend fun getAll(): List<ReminderState> = reminderStateDao.getAll().map { it.toDomain() }

        override suspend fun get(key: OccurrenceKey): ReminderState? =
            reminderStateDao.getByKey(key.encode())?.toDomain()

        override suspend fun upsert(state: ReminderState) {
            reminderStateDao.upsert(state.toEntity())
        }

        override suspend fun delete(key: OccurrenceKey) {
            reminderStateDao.delete(key.encode())
        }
    }
