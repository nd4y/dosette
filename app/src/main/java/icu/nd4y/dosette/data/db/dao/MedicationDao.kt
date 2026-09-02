package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.MedicationEntity
import icu.nd4y.dosette.data.db.entity.MedicationWithDetails
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface MedicationDao {
    @Transaction
    @Query("SELECT * FROM medications WHERE profileId = :profileId ORDER BY name")
    fun observeByProfile(profileId: String): Flow<List<MedicationWithDetails>>

    /** All profiles at once — reminder planning is profile-agnostic. */
    @Transaction
    @Query("SELECT * FROM medications WHERE archivedAt IS NULL")
    suspend fun getAllActiveWithDetails(): List<MedicationWithDetails>

    @Transaction
    @Query("SELECT * FROM medications")
    suspend fun getAllWithDetails(): List<MedicationWithDetails>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    fun observeWithDetails(id: String): Flow<MedicationWithDetails?>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: String): MedicationEntity?

    @Upsert
    suspend fun upsert(medication: MedicationEntity)

    @Query("UPDATE medications SET archivedAt = :at WHERE id = :id")
    suspend fun archive(
        id: String,
        at: Instant,
    )

    @Query("UPDATE medications SET archivedAt = NULL WHERE id = :id")
    suspend fun unarchive(id: String)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: String)
}
