package icu.nd4y.dosette.testing

import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import icu.nd4y.dosette.reminders.notifications.ReminderPayload

/** Records every call instead of touching the notification manager. */
class FakeReminderNotifier : ReminderNotifier {
    /** Posted dose reminders with their alert flag. */
    val reminders = mutableListOf<Pair<ReminderPayload, Boolean>>()
    val cancelled = mutableListOf<OccurrenceKey>()
    val missedNotices = mutableListOf<OccurrenceKey>()

    /** Medication ids that got a low-stock notice. */
    val lowStock = mutableListOf<String>()

    /** Posted appointment notices with their offset. */
    val appointments = mutableListOf<Pair<Appointment, Int>>()

    /** [cancelAppointment] calls: appointment id to the offsets it was asked to drop. */
    val cancelledAppointments = mutableListOf<Pair<String, List<Int>>>()

    var cancelAllCalls = 0
        private set

    override fun postReminder(
        payload: ReminderPayload,
        alert: Boolean,
    ) {
        reminders += payload to alert
    }

    override fun cancelReminder(key: OccurrenceKey) {
        cancelled += key
    }

    override fun postMissedNotice(
        key: OccurrenceKey,
        medicationTitle: String,
    ) {
        missedNotices += key
    }

    override fun postLowStock(
        medicationId: String,
        medicationTitle: String,
        unitsLeft: String,
    ) {
        lowStock += medicationId
    }

    override fun postAppointment(
        appointment: Appointment,
        offsetMin: Int,
    ) {
        appointments += appointment to offsetMin
    }

    override fun cancelAll() {
        cancelAllCalls++
    }

    override fun postLockedNotice() = Unit

    override fun cancelLockedNotice() = Unit

    override fun cancelAppointment(
        appointmentId: String,
        offsetsMin: List<Int>,
    ) {
        cancelledAppointments += appointmentId to offsetsMin
    }
}
