package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * No FK to medications on purpose: the reminder engine resolves and cleans
 * up states itself, and a cascade delete mid-nag would silently drop a
 * notification without cancelling it.
 */
@Entity(tableName = "reminder_states")
data class ReminderStateEntity(
    /** Encoded [icu.nd4y.dosette.domain.model.OccurrenceKey]. */
    @PrimaryKey val occurrenceKey: String,
    val medicationId: String,
    val profileId: String,
    val scheduledAt: Instant,
    val phase: String,
    val snoozedUntil: Instant?,
    val nagCount: Int,
    val firstNotifiedAt: Instant,
    val lastAlertAt: Instant,
)
