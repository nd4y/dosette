package icu.nd4y.dosette.domain.model

import java.time.Instant

/**
 * Persistent nag state of one active reminder. A row exists only while the
 * occurrence is unresolved; Take/Skip/grace-expiry deletes it. Survives
 * process death and reboot so notifications can be re-posted.
 */
data class ReminderState(
    val occurrenceKey: OccurrenceKey,
    val medicationId: String,
    val profileId: String,
    val scheduledAt: Instant,
    val phase: ReminderPhase,
    val snoozedUntil: Instant?,
    /** Set instead of [snoozedUntil] when snoozed until a place is reached. */
    val snoozedUntilPlace: PlaceId?,
    /**
     * Start of the missed-grace window. Equals [scheduledAt] normally; a
     * place-snooze moves it to the wake-up moment, otherwise "snooze until
     * home" would silently expire into MISSED while still commuting.
     */
    val graceAnchor: Instant,
    val nagCount: Int,
    val firstNotifiedAt: Instant,
    val lastAlertAt: Instant,
)

enum class ReminderPhase {
    ACTIVE,
    SNOOZED,
}
