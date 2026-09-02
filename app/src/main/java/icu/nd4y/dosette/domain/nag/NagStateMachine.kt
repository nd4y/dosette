package icu.nd4y.dosette.domain.nag

import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

data class NagSettings(
    val nagIntervalMin: Int,
    /** Cap on audible repeats; [NO_NAG_CAP] = the grace window alone ends them. */
    val nagMaxCount: Int,
    val snoozeMin: Int,
    val missedGraceMin: Int,
) {
    /** May another nag follow [nagCount] alerts? */
    fun nagAllowed(nagCount: Int): Boolean = nagMaxCount == NO_NAG_CAP || nagCount + 1 < nagMaxCount

    companion object {
        const val NO_NAG_CAP = 0
    }
}

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

    /** User postponed the reminder for a fixed time or until a place. */
    data class Snooze(
        val target: SnoozeTarget,
    ) : NagEvent

    /** The user swiped the notification away (deleteIntent fired). */
    data object Dismissed : NagEvent

    /** The snooze period ended. */
    data object SnoozeExpired : NagEvent

    /** The device arrived at a place a reminder was snoozed until. */
    data class PlaceReached(
        val place: PlaceId,
    ) : NagEvent

    /** The grace window closed without an action. */
    data object GraceExpired : NagEvent
}

sealed interface SnoozeTarget {
    data class ForMinutes(
        val minutes: Int,
    ) : SnoozeTarget

    data class UntilPlace(
        val place: PlaceId,
    ) : SnoozeTarget
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
    /** Longest wait for a place before the reminder comes back on its own. */
    private val PLACE_SNOOZE_CEILING: Duration = Duration.ofHours(PLACE_SNOOZE_CEILING_HOURS)

    fun reduce(
        state: ReminderState?,
        event: NagEvent,
        now: Instant,
        settings: NagSettings,
    ): Transition =
        when (event) {
            is NagEvent.OccurrenceDue -> {
                onDue(state, event, now)
            }

            NagEvent.NagTick -> {
                onNagTick(state, now, settings)
            }

            NagEvent.Take -> {
                resolve(DoseStatus.TAKEN)
            }

            NagEvent.Skip -> {
                resolve(DoseStatus.SKIPPED)
            }

            is NagEvent.Snooze -> {
                onSnooze(state, event.target, now)
            }

            NagEvent.Dismissed -> {
                onDismissed(state)
            }

            NagEvent.SnoozeExpired -> {
                onSnoozeExpired(state, now)
            }

            is NagEvent.PlaceReached -> {
                onPlaceReached(state, event.place, now)
            }

            // A snoozed reminder restarts its window when it wakes, so grace
            // may only end an active one; the engine checks the same, this
            // keeps the reducer sound on its own.
            NagEvent.GraceExpired -> {
                if (state?.phase == ReminderPhase.ACTIVE) expire(state) else Transition(state, emptyList())
            }
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
                snoozedUntilPlace = null,
                graceAnchor = event.scheduledAt,
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
            now.isAfter(state.graceAnchor.plus(settings.missedGraceMin.toLong(), ChronoUnit.MINUTES))
        val exhausted = !settings.nagAllowed(state.nagCount)
        return when {
            graceOver -> {
                expire(state)
            }

            // Nags are used up: stop alerting but keep the dose actionable.
            // Only GraceExpired finalizes it — a stray tick delivered by an
            // unrelated engine pass must not mark the dose missed early.
            exhausted -> {
                Transition(state, emptyList())
            }

            else -> {
                val nagged = state.copy(nagCount = state.nagCount + 1, lastAlertAt = now)
                Transition(nagged, listOf(NagEffect.PostReminder(alert = true), NagEffect.Reschedule))
            }
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
        target: SnoozeTarget,
        now: Instant,
    ): Transition {
        if (state == null) return Transition(null, emptyList())
        val snoozed =
            when (target) {
                is SnoozeTarget.ForMinutes -> {
                    state.copy(
                        phase = ReminderPhase.SNOOZED,
                        snoozedUntil = now.plus(target.minutes.toLong(), ChronoUnit.MINUTES),
                        snoozedUntilPlace = null,
                    )
                }

                is SnoozeTarget.UntilPlace -> {
                    // The ceiling keeps a geofence that never fires (no
                    // background location, a cleared place) from letting the
                    // dose wait forever.
                    state.copy(
                        phase = ReminderPhase.SNOOZED,
                        snoozedUntil = now.plus(PLACE_SNOOZE_CEILING),
                        snoozedUntilPlace = target.place,
                    )
                }
            }
        return Transition(snoozed, listOf(NagEffect.CancelReminder, NagEffect.Reschedule))
    }

    private fun onPlaceReached(
        state: ReminderState?,
        place: PlaceId,
        now: Instant,
    ): Transition {
        if (state == null || state.phase != ReminderPhase.SNOOZED || state.snoozedUntilPlace != place) {
            return Transition(state, emptyList())
        }
        // Arriving restarts the grace window: the wait for the place was
        // deliberate, so the user gets the full window to act from now on.
        val active =
            state.copy(
                phase = ReminderPhase.ACTIVE,
                snoozedUntil = null,
                snoozedUntilPlace = null,
                graceAnchor = now,
                nagCount = 0,
                lastAlertAt = now,
            )
        return Transition(active, listOf(NagEffect.PostReminder(alert = true), NagEffect.Reschedule))
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
        // A place snooze wakes at its ceiling the same way, place or not.
        if (state == null || state.phase != ReminderPhase.SNOOZED) {
            return Transition(state, emptyList())
        }
        // Same rule as arriving at a place: the postponement was deliberate,
        // so the wake restarts both the grace window and the nag budget —
        // otherwise a snooze reaching past the original grace end would be
        // finalized missed the moment it wakes up.
        val active =
            state.copy(
                phase = ReminderPhase.ACTIVE,
                snoozedUntil = null,
                snoozedUntilPlace = null,
                graceAnchor = now,
                nagCount = 0,
                lastAlertAt = now,
            )
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

private const val PLACE_SNOOZE_CEILING_HOURS = 12L
