package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.ReminderStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderStateDao {
    @Query("SELECT * FROM reminder_states")
    suspend fun getAll(): List<ReminderStateEntity>

    @Query("SELECT * FROM reminder_states")
    fun observeAll(): Flow<List<ReminderStateEntity>>

    @Query("SELECT * FROM reminder_states WHERE occurrenceKey = :occurrenceKey")
    suspend fun getByKey(occurrenceKey: String): ReminderStateEntity?

    @Upsert
    suspend fun upsert(state: ReminderStateEntity)

    @Query("DELETE FROM reminder_states WHERE occurrenceKey = :occurrenceKey")
    suspend fun delete(occurrenceKey: String)
}
