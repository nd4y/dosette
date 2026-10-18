package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Latest user intent for an occurrence, retained when its dose log is undone or replaced. */
@Entity(tableName = "dose_action_cursors")
data class DoseActionCursorEntity(
    @PrimaryKey val occurrenceKey: String,
    val actedAt: Long,
)
