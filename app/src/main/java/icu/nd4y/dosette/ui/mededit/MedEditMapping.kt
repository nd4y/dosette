package icu.nd4y.dosette.ui.mededit

import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.domain.model.Schedule
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.ui.common.formatAmount
import java.time.LocalTime

/**
 * The schedule the wizard edits: the open-ended version. One-off doses
 * are single-day schedules (startDate == endDate) and closed versions
 * are history — neither is the medication's "current" schedule.
 */
fun mainScheduleOf(schedules: List<Schedule>): Schedule? =
    schedules.filter { it.endDate == null }.maxByOrNull { it.createdAt }

/** Wizard state pre-filled from an existing medication for the edit flow. */
fun prefillFrom(details: MedicationDetails): MedEditUiState {
    val med = details.medication
    val schedule = mainScheduleOf(details.schedules)
    val tracked = details.variants.filter { it.trackingEnabled }.sortedBy { it.sortOrder }
    val strengthText = med.strengthValue?.let(::formatAmount).orEmpty()
    return MedEditUiState(
        editing = true,
        name = med.name,
        form = med.form,
        strengthText = strengthText,
        strengthUnit = med.strengthUnit.orEmpty(),
        instructions = med.instructions.orEmpty(),
        colorSeed = med.colorSeed,
        scheduleType = schedule?.type ?: ScheduleType.FIXED_TIMES,
        weekdays = schedule?.weekdays ?: emptySet(),
        intervalText = (schedule?.intervalDays ?: DEFAULT_INTERVAL_DAYS).toString(),
        cycleOnText = (schedule?.cycleDaysOn ?: DEFAULT_CYCLE_ON).toString(),
        cycleOffText = (schedule?.cycleDaysOff ?: DEFAULT_CYCLE_OFF).toString(),
        times =
            schedule
                ?.times
                ?.sortedBy { it.time }
                ?.map { TimeSlotDraft(time = it.time, amountText = formatAmount(it.doseAmount)) }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(TimeSlotDraft(LocalTime.of(DEFAULT_HOUR, 0))),
        trackStock = tracked.isNotEmpty(),
        variants =
            if (tracked.isEmpty()) {
                listOf(VariantDraft(strengthText = strengthText))
            } else {
                tracked.map { variant ->
                    VariantDraft(
                        id = variant.id,
                        strengthText = variant.strengthValue?.let(::formatAmount).orEmpty(),
                        stockText = formatAmount(variant.currentStock),
                        thresholdText = variant.lowStockThreshold?.let(::formatAmount).orEmpty(),
                        refillText = variant.defaultRefillAmount?.let(::formatAmount).orEmpty(),
                    )
                }
            },
    )
}

/**
 * True when [built] describes the same intake plan as [existing] —
 * then an edit must NOT spawn a new schedule version. Dates and ids
 * are irrelevant: only the pattern and the dose slots matter.
 */
fun scheduleMatches(
    existing: Schedule,
    built: Schedule,
): Boolean {
    if (existing.type != built.type) return false
    val slotsMatch =
        existing.times.sortedBy { it.time }.map { it.time to it.doseAmount } ==
            built.times.sortedBy { it.time }.map { it.time to it.doseAmount }
    return when (built.type) {
        ScheduleType.FIXED_TIMES -> {
            slotsMatch
        }

        ScheduleType.WEEKDAYS -> {
            existing.weekdays == built.weekdays && slotsMatch
        }

        ScheduleType.EVERY_N_DAYS -> {
            existing.intervalDays == built.intervalDays && slotsMatch
        }

        ScheduleType.CYCLE -> {
            existing.cycleDaysOn == built.cycleDaysOn &&
                existing.cycleDaysOff == built.cycleDaysOff &&
                slotsMatch
        }

        ScheduleType.AS_NEEDED -> {
            true
        }
    }
}

private const val DEFAULT_INTERVAL_DAYS = 2
private const val DEFAULT_CYCLE_ON = 21
private const val DEFAULT_CYCLE_OFF = 7
private const val DEFAULT_HOUR = 8
