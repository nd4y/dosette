package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AppointmentDao {
    @Query(
        "SELECT * FROM appointments WHERE profileId = :profileId AND date >= :from " +
            "ORDER BY date, timeMinutes",
    )
    fun observeUpcoming(
        profileId: String,
        from: LocalDate,
    ): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE date >= :from ORDER BY date, timeMinutes")
    suspend fun getAllFrom(from: LocalDate): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: String): AppointmentEntity?

    @Upsert
    suspend fun upsert(appointment: AppointmentEntity)

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun delete(id: String)
}
