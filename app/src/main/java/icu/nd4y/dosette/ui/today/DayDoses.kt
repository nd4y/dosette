package icu.nd4y.dosette.ui.today

import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator
import icu.nd4y.dosette.ui.common.formatAmount
import icu.nd4y.dosette.ui.common.strengthLabel
import java.time.LocalDate
import java.time.ZoneId

/**
 * Consecutive runs of the same slot, in time order. Slots are NOT rendered
 * in fixed enum order: a midnight dose belongs to the start of the day, so
 * the night slot can appear both first (00:xx) and last (23:xx).
 */
fun slotSections(doses: List<TodayDose>): List<List<TodayDose>> {
    val sections = mutableListOf<MutableList<TodayDose>>()
    doses.forEach { dose ->
        val last = sections.lastOrNull()
        if (last == null || last.last().slot != dose.slot) {
            sections.add(mutableListOf(dose))
        } else {
            last.add(dose)
        }
    }
    return sections
}

/**
 * Planned occurrences of [date] merged with the day's logs — the single
 * source for both the Today timeline and the calendar day sheet.
 */
fun buildDayDoses(
    date: LocalDate,
    meds: List<MedicationDetails>,
    logs: List<DoseLog>,
    zone: ZoneId,
): List<TodayDose> {
    // An archived medication disappears from the archive date on, but its
    // history stays: past days keep showing what was planned and logged.
    val active =
        meds.filter { med ->
            val archivedOn =
                med.medication.archivedAt
                    ?.atZone(zone)
                    ?.toLocalDate()
            archivedOn == null || date.isBefore(archivedOn)
        }
    val logByKey =
        logs
            .filter { it.kind == DoseKind.SCHEDULED && it.time != null && it.date == date }
            .associateBy { OccurrenceKey(it.medicationId, it.date, requireNotNull(it.time)) }

    return active
        .flatMap { med ->
            val schedulesById = med.schedules.associateBy { it.id }
            OccurrenceGenerator
                .occurrencesOn(med.schedulesActiveOn(date), date)
                .map { occurrence ->
                    val log = logByKey[occurrence.key]
                    val schedule = schedulesById[occurrence.scheduleId]
                    TodayDose(
                        medicationId = med.medication.id,
                        date = date,
                        time = occurrence.time,
                        name = med.medication.name,
                        strengthText = strengthLabel(med.medication.strengthValue, med.medication.strengthUnit),
                        amountText = formatAmount(occurrence.amount),
                        instructions = med.medication.instructions,
                        form = med.medication.form,
                        colorSeed = med.medication.colorSeed,
                        status =
                            when (log?.status) {
                                DoseStatus.TAKEN -> DoseUiStatus.TAKEN
                                DoseStatus.SKIPPED -> DoseUiStatus.SKIPPED
                                DoseStatus.MISSED -> DoseUiStatus.MISSED
                                null -> DoseUiStatus.PENDING
                            },
                        actedTime = log?.actedAt?.atZone(zone)?.toLocalTime(),
                        scheduleId = occurrence.scheduleId,
                        oneOff = schedule != null && schedule.startDate == schedule.endDate,
                    )
                }
        }
        // Occurrence identity is (medication, date, time): two schedules
        // producing the same slot are one dose for the logs and must be one
        // row here — otherwise the list keys collide. The regular schedule
        // wins over a one-off.
        .sortedWith(compareBy({ it.time }, { it.oneOff }))
        .distinctBy { it.key }
}
