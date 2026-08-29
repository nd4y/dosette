package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY sortOrder, createdAt")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    /** Cascades to medications, schedules, dose logs and appointments. */
    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)
}
