package icu.nd4y.dosette.ui.meddetail

import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.stats.AdherenceCalculator
import java.time.LocalDate

/** One cell of the medication's recent-intake strip. */
data class AdherenceDay(
    val date: LocalDate,
    val status: AdherenceCalculator.DayStatus,
)

/** Day-by-day statuses of one medication over [from]..[to], oldest first. */
fun adherenceDays(
    logs: List<DoseLog>,
    medicationId: String,
    from: LocalDate,
    to: LocalDate,
): List<AdherenceDay> {
    val byDate = scheduledOf(logs, medicationId).groupBy { it.date }
    return generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .map { date ->
            val day = byDate[date].orEmpty()
            AdherenceDay(
                date = date,
                status =
                    AdherenceCalculator.dayStatus(
                        taken = day.count { it.status == DoseStatus.TAKEN },
                        skipped = day.count { it.status == DoseStatus.SKIPPED },
                        missed = day.count { it.status == DoseStatus.MISSED },
                    ),
            )
        }.toList()
}

/** Taken-vs-missed percentage for one medication; null with no data. */
fun adherencePercent(
    logs: List<DoseLog>,
    medicationId: String,
): Int? {
    val own = scheduledOf(logs, medicationId)
    return AdherenceCalculator.percent(
        taken = own.count { it.status == DoseStatus.TAKEN },
        missed = own.count { it.status == DoseStatus.MISSED },
    )
}

private fun scheduledOf(
    logs: List<DoseLog>,
    medicationId: String,
): List<DoseLog> = logs.filter { it.medicationId == medicationId && it.kind == DoseKind.SCHEDULED }
