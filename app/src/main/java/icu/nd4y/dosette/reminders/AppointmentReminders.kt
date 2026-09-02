package icu.nd4y.dosette.reminders

import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Notices of upcoming visits. A reminder is posted even when the pass runs
 * late (phone off, inexact fallback) as long as the visit has not begun;
 * the watermark — the latest reminder actually posted — keeps a later pass
 * from re-sounding it, while one created seconds after a pass stays fresh.
 */
internal class AppointmentReminders(
    private val notifier: ReminderNotifier,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun postDue(
        appointments: List<Appointment>,
        sweepMark: Instant?,
        now: Instant,
        slackEnd: Instant,
        zone: ZoneId,
    ) {
        var latestPosted: Instant? = null
        for (appointment in appointments) {
            val startsAt =
                appointment.date
                    .atTime(appointment.time)
                    .atZone(zone)
                    .toInstant()
            // A notice for a visit that has begun is pointless.
            if (!startsAt.isAfter(now)) continue
            val posted = postOffsets(appointment, startsAt, sweepMark, slackEnd)
            if (posted != null && (latestPosted == null || posted.isAfter(latestPosted))) latestPosted = posted
        }
        latestPosted?.let { settingsRepository.setLastAppointmentSweepAt(it) }
    }

    /** Posts every due, not yet posted offset of [appointment]; returns the latest one posted. */
    private fun postOffsets(
        appointment: Appointment,
        startsAt: Instant,
        sweepMark: Instant?,
        slackEnd: Instant,
    ): Instant? {
        var latest: Instant? = null
        for (offset in appointment.reminderOffsetsMin) {
            val remindAt = startsAt.minus(Duration.ofMinutes(offset.toLong()))
            val fresh = sweepMark?.let { remindAt.isAfter(it) } ?: true
            if (fresh && !remindAt.isAfter(slackEnd)) {
                notifier.postAppointment(appointment, offset)
                if (latest == null || remindAt.isAfter(latest)) latest = remindAt
            }
        }
        return latest
    }
}
