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
    activeReminders: Set<OccurrenceKey> = emptySet(),
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
            val planned = plannedDoses(med, date, logByKey, zone, activeReminders)
            planned + historyDoses(med, date, logByKey, planned, zone)
        }
        // Occurrence identity is (medication, date, time): two schedules
        // producing the same slot are one dose for the logs and must be one
        // row here — otherwise the list keys collide. The regular schedule
        // wins over a one-off.
        .sortedWith(compareBy({ it.time }, { it.oneOff }))
        .distinctBy { it.key }
}

/** The day's plan for [med], with the marks of its logs. */
private fun plannedDoses(
    med: MedicationDetails,
    date: LocalDate,
    logByKey: Map<OccurrenceKey, DoseLog>,
    zone: ZoneId,
    activeReminders: Set<OccurrenceKey>,
): List<TodayDose> {
    val schedulesById = med.schedules.associateBy { it.id }
    // A slot the plan did not have when its time came (a version inserted
    // after it) is not a dose of the day — unless it was logged under an
    // earlier version, which is history to keep.
    val planned =
        OccurrenceGenerator
            .plannedOccurrencesOn(med.schedules, date, zone)
            .mapTo(HashSet()) { it.key }
    return OccurrenceGenerator
        .occurrencesOn(med.schedules, date)
        .filter { it.key in planned || it.key in logByKey }
        .map { occurrence ->
            val log = logByKey[occurrence.key]
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
                status = uiStatus(log?.status),
                actedTime = log?.actedAt?.atZone(zone)?.toLocalTime(),
                scheduleId = occurrence.scheduleId,
                oneOff = schedulesById[occurrence.scheduleId]?.oneOff == true,
                reminderActive = occurrence.key in activeReminders,
            )
        }
}

/**
 * Marks whose slot no version produces any more (the time moved in a
 * same-day edit, the version was swapped out) are listed as history, so
 * the day and the ring agree with the statistics, which count every log.
 */
private fun historyDoses(
    med: MedicationDetails,
    date: LocalDate,
    logByKey: Map<OccurrenceKey, DoseLog>,
    planned: List<TodayDose>,
    zone: ZoneId,
): List<TodayDose> {
    val plannedKeys = planned.mapTo(HashSet()) { it.key }
    return logByKey.keys
        .filter { it.medicationId == med.medication.id && it !in plannedKeys }
        .map { key ->
            val log = logByKey.getValue(key)
            TodayDose(
                medicationId = med.medication.id,
                date = date,
                time = key.time,
                name = med.medication.name,
                strengthText = strengthLabel(med.medication.strengthValue, med.medication.strengthUnit),
                amountText = formatAmount(log.amount),
                instructions = med.medication.instructions,
                form = med.medication.form,
                colorSeed = med.medication.colorSeed,
                status = uiStatus(log.status),
                actedTime = log.actedAt?.atZone(zone)?.toLocalTime(),
                scheduleId = log.scheduleId,
                oneOff = false,
            )
        }
}

private fun uiStatus(status: DoseStatus?): DoseUiStatus =
    when (status) {
        DoseStatus.TAKEN -> DoseUiStatus.TAKEN
        DoseStatus.SKIPPED -> DoseUiStatus.SKIPPED
        DoseStatus.MISSED -> DoseUiStatus.MISSED
        null -> DoseUiStatus.PENDING
    }
