package icu.nd4y.dosette.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DoseLog(
    val id: String,
    val profileId: String,
    val medicationId: String,
    val scheduleId: String?,
    val kind: DoseKind,
    val date: LocalDate,
    /** Planned wall-clock time; null for PRN intakes. */
    val time: LocalTime?,
    val scheduledAt: Instant?,
    val status: DoseStatus,
    val actedAt: Instant?,
    val amount: Double,
    val note: String?,
    val updatedAt: Instant,
)

enum class DoseStatus {
    TAKEN,
    SKIPPED,
    MISSED,
}

enum class DoseKind {
    SCHEDULED,
    PRN,
}

/**
 * Identity of a scheduled occurrence: wall-clock based so it survives
 * timezone changes (an epoch-based key would not).
 */
data class OccurrenceKey(
    val medicationId: String,
    val date: LocalDate,
    val time: LocalTime,
) {
    fun encode(): String = "$medicationId|$date|${time.format(TIME_FORMAT)}"

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        fun decode(value: String): OccurrenceKey {
            val (medicationId, date, time) = value.split('|')
            return OccurrenceKey(medicationId, LocalDate.parse(date), LocalTime.parse(time, TIME_FORMAT))
        }
    }
}
