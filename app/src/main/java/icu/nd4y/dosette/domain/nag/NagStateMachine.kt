package icu.nd4y.dosette.domain.nag

import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import java.time.Instant
import java.time.temporal.ChronoUnit

data class NagSettings(
    val nagIntervalMin: Int,
    val nagMaxCount: Int,
    val snoozeMin: Int,
    val missedGraceMin: Int,
)

sealed interface NagEvent {
    /** A scheduled occurrence just became due. */
    data class OccurrenceDue(
        val key: OccurrenceKey,
        val medicationId: String,
        val profileId: String,
        val scheduledAt: Instant,
    ) : NagEvent

    /** The nag interval elapsed for an active reminder. */
    data object NagTick : NagEvent

    /** User marked the dose taken. */
    data object Take : NagEvent

    /** User deliberately skipped the dose. */
    data object Skip : NagEvent

    /** User postponed the reminder. */
    data object Snooze : NagEvent

    /** The user swiped the notification away (deleteIntent fired). */
    data object Dismissed : NagEvent

    /** The snooze period ended. */
    data object SnoozeExpired : NagEvent

    /** The grace window closed without an action. */
    data object GraceExpired : NagEvent
}

sealed interface NagEffect {
    /** Post (or re-post) the reminder notification; [alert] = with sound, else silent channel. */
    data class PostReminder(
        val alert: Boolean,
    ) : NagEffect

    data object CancelReminder : NagEffect

    /** Write the dose log with the given final status. */
    data class FinalizeDose(
        val status: DoseStatus,
    ) : NagEffect

    data object DecrementStock : NagEffect

    /** Passive, dismissable "missed" notice. */
    data object PostMissedNotice : NagEffect

    data object Reschedule : NagEffect
}

data class Transition(
    val state: ReminderState?,
    val effects: List<NagEffect>,
)

/**
 * Pure reducer behind the non-dismissable reminder behavior. The engine
 * resolves the persisted state by occurrence key, applies an event here and
 * executes the returned effects; the reducer itself never touches Android.
 *
 * Key asymmetry that keeps this sound: the deleteIntent (-> [NagEvent.Dismissed])
 * fires only on a user swipe, never on the app's own cancel.
 */
object NagStateMachine {
    fun reduce(
        state: ReminderState?,
        event: NagEvent,
        now: Instant,
        settings: NagSettings,
    ): Transition =
        when (event) {
            is NagEvent.OccurrenceDue -> onDue(state, event, now)
            NagEvent.NagTick -> onNagTick(state, now, settings)
            NagEvent.Take -> resolve(DoseStatus.TAKEN)
            NagEvent.Skip -> resolve(DoseStatus.SKIPPED)
            NagEvent.Snooze -> onSnooze(state, now, settings)
            NagEvent.Dismissed -> onDismissed(state)
            NagEvent.SnoozeExpired -> onSnoozeExpired(state, now)
            NagEvent.GraceExpired -> expire(state)
        }

    private fun onDue(
        state: ReminderState?,
        event: NagEvent.OccurrenceDue,
        now: Instant,
    ): Transition {
        // Double delivery of the same occurrence must be a no-op.
        if (state != null) return Transition(state, emptyList())
        val fresh =
            ReminderState(
                occurrenceKey = event.key,
                medicationId = event.medicationId,
                profileId = event.profileId,
                scheduledAt = event.scheduledAt,
                phase = ReminderPhase.ACTIVE,
                snoozedUntil = null,
                nagCount = 0,
                firstNotifiedAt = now,
                lastAlertAt = now,
            )
        return Transition(fresh, listOf(NagEffect.PostReminder(alert = true), NagEffect.Reschedule))
    }

    private fun onNagTick(
        state: ReminderState?,
        now: Instant,
        settings: NagSettings,
    ): Transition {
        if (state == null || state.phase != ReminderPhase.ACTIVE) {
            return Transition(state, emptyList())
        }
        val graceOver =
            now.isAfter(state.scheduledAt.plus(settings.missedGraceMin.toLong(), ChronoUnit.MINUTES))
        val exhausted = state.nagCount + 1 >= settings.nagMaxCount
        return if (graceOver || exhausted) {
            expire(state)
        } else {
            val nagged = state.copy(nagCount = state.nagCount + 1, lastAlertAt = now)
            Transition(nagged, listOf(NagEffect.PostReminder(alert = true), NagEffect.Reschedule))
        }
    }

    private fun resolve(status: DoseStatus): Transition {
        val effects =
            buildList {
                add(NagEffect.FinalizeDose(status))
                if (status == DoseStatus.TAKEN) add(NagEffect.DecrementStock)
                add(NagEffect.CancelReminder)
                add(NagEffect.Reschedule)
            }
        return Transition(null, effects)
    }

    private fun onSnooze(
        state: ReminderState?,
        now: Instant,
        settings: NagSettings,
    ): Transition {
        if (state == null) return Transition(null, emptyList())
        val snoozed =
            state.copy(
                phase = ReminderPhase.SNOOZED,
                snoozedUntil = now.plus(settings.snoozeMin.toLong(), ChronoUnit.MINUTES),
            )
        return Transition(snoozed, listOf(NagEffect.CancelReminder, NagEffect.Reschedule))
    }

    private fun onDismissed(state: ReminderState?): Transition =
        if (state != null && state.phase == ReminderPhase.ACTIVE) {
            // Swipe does not resolve the dose: silent immediate re-post, the
            // next nag tick re-alerts with sound on its own cadence.
            Transition(state, listOf(NagEffect.PostReminder(alert = false)))
        } else {
            Transition(state, emptyList())
        }

    private fun onSnoozeExpired(
        state: ReminderState?,
        now: Instant,
    ): Transition {
        if (state == null || state.phase != ReminderPhase.SNOOZED) {
            return Transition(state, emptyList())
        }
        val active = state.copy(phase = ReminderPhase.ACTIVE, snoozedUntil = null, lastAlertAt = now)
        return Transition(active, listOf(NagEffect.PostReminder(alert = true), NagEffect.Reschedule))
    }

    private fun expire(state: ReminderState?): Transition {
        if (state == null) return Transition(null, emptyList())
        return Transition(
            null,
            listOf(
                NagEffect.CancelReminder,
                NagEffect.FinalizeDose(DoseStatus.MISSED),
                NagEffect.PostMissedNotice,
                NagEffect.Reschedule,
            ),
        )
    }
}
