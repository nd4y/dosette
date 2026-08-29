package icu.nd4y.dosette.reminders.notifications

import icu.nd4y.dosette.domain.model.OccurrenceKey

object NotificationIds {
    fun reminder(key: OccurrenceKey): Int = key.encode().hashCode()

    fun missedNotice(key: OccurrenceKey): Int = "missed|${key.encode()}".hashCode()

    fun lowStock(medicationId: String): Int = "stock|$medicationId".hashCode()

    fun appointment(
        appointmentId: String,
        offsetMin: Int,
    ): Int = "appt|$appointmentId|$offsetMin".hashCode()
}
