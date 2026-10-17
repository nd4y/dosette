package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.DoseActionCursorEntity
import icu.nd4y.dosette.data.db.entity.WatchActionReceiptEntity

@Dao
interface WatchActionReceiptDao {
    @Query("SELECT EXISTS(SELECT 1 FROM watch_action_receipts WHERE id = :id)")
    suspend fun contains(id: String): Boolean

    @Insert
    suspend fun insert(receipt: WatchActionReceiptEntity)

    @Query("SELECT actedAt FROM dose_action_cursors WHERE occurrenceKey = :key")
    suspend fun latestActionAt(key: String): Long?

    @Upsert
    suspend fun upsertCursor(cursor: DoseActionCursorEntity)
}
