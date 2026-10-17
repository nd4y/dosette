package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local transport receipts survive undo and backup import; never exported with medical data. */
@Entity(tableName = "watch_action_receipts")
data class WatchActionReceiptEntity(
    @PrimaryKey val id: String,
)
