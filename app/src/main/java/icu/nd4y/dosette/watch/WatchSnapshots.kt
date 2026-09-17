package icu.nd4y.dosette.watch

import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.link.WatchDose
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.link.WatchPrn
import icu.nd4y.dosette.link.WatchSnapshot
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.widget.WidgetState

/**
 * The widget's picture of the day in the wire format the watch reads.
 * Yesterday's unresolved doses come first, as on the widget; the watch
 * lists them under their own header and does not count them.
 */
fun buildWatchSnapshot(
    state: WidgetState,
    activeReminders: Set<OccurrenceKey>,
    profileName: String?,
): WatchSnapshot =
    WatchSnapshot(
        date = state.date.toString(),
        profileName = profileName,
        doses =
            state.carryover.map { it.toWire(carryover = true, activeReminders) } +
                state.doses.map { it.toWire(carryover = false, activeReminders) },
        prn = state.prn.map { WatchPrn(it.medicationId, listOfNotNull(it.name, it.strengthText).joinToString(" ")) },
    )

private fun TodayDose.toWire(
    carryover: Boolean,
    activeReminders: Set<OccurrenceKey>,
): WatchDose =
    WatchDose(
        id = key.encode(),
        title = listOfNotNull(name, strengthText).joinToString(" "),
        amount = amountText,
        timeMinutes = time.toSecondOfDay() / SECONDS_PER_MINUTE,
        status =
            when (status) {
                DoseUiStatus.PENDING -> WatchDoseStatus.PENDING
                DoseUiStatus.TAKEN -> WatchDoseStatus.TAKEN
                DoseUiStatus.SKIPPED -> WatchDoseStatus.SKIPPED
                DoseUiStatus.MISSED -> WatchDoseStatus.MISSED
            },
        actedTimeMinutes = actedTime?.let { it.toSecondOfDay() / SECONDS_PER_MINUTE },
        carryover = carryover,
        reminderActive = key in activeReminders,
    )

private const val SECONDS_PER_MINUTE = 60
