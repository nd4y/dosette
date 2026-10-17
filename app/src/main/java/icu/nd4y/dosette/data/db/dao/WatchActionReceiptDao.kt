package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import icu.nd4y.dosette.data.db.entity.WatchActionReceiptEntity

@Dao
interface WatchActionReceiptDao {
    @Query("SELECT EXISTS(SELECT 1 FROM watch_action_receipts WHERE id = :id)")
    suspend fun contains(id: String): Boolean

    @Insert
    suspend fun insert(receipt: WatchActionReceiptEntity)
}
