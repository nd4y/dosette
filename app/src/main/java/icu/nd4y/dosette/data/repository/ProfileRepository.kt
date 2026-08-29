package icu.nd4y.dosette.data.repository

import icu.nd4y.dosette.data.db.dao.ProfileDao
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.data.db.toEntity
import icu.nd4y.dosette.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ProfileRepository {
    fun observeAll(): Flow<List<Profile>>

    suspend fun getAll(): List<Profile>

    suspend fun getById(id: String): Profile?

    suspend fun upsert(profile: Profile)

    /** Destructive: cascades to the profile's medications, logs and appointments. */
    suspend fun delete(id: String)
}

@Singleton
class ProfileRepositoryImpl
    @Inject
    constructor(
        private val profileDao: ProfileDao,
    ) : ProfileRepository {
        override fun observeAll(): Flow<List<Profile>> =
            profileDao.observeAll().map { profiles -> profiles.map { it.toDomain() } }

        override suspend fun getAll(): List<Profile> = profileDao.getAll().map { it.toDomain() }

        override suspend fun getById(id: String): Profile? = profileDao.getById(id)?.toDomain()

        override suspend fun upsert(profile: Profile) = profileDao.upsert(profile.toEntity())

        override suspend fun delete(id: String) = profileDao.delete(id)
    }
