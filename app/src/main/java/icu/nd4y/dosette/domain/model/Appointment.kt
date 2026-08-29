package icu.nd4y.dosette.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class Appointment(
    val id: String,
    val profileId: String,
    val title: String,
    val doctorName: String?,
    val location: String?,
    val date: LocalDate,
    val time: LocalTime,
    val notes: String?,
    /** Reminder offsets in minutes before the appointment, e.g. [1440, 120]. */
    val reminderOffsetsMin: List<Int>,
    val createdAt: Instant,
)
