package icu.nd4y.dosette.domain.nag

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class NagStateMachineTest {
    private val settings = NagSettings(nagIntervalMin = 10, nagMaxCount = 6, snoozeMin = 10, missedGraceMin = 60)
    private val scheduledAt = Instant.parse("2026-08-29T17:00:00Z")
    private val key = OccurrenceKey("m1", LocalDate.parse("2026-08-29"), LocalTime.of(20, 0))
    private val due = NagEvent.OccurrenceDue(key, medicationId = "m1", profileId = "p1", scheduledAt = scheduledAt)

    private fun activeState(
        nagCount: Int = 0,
        lastAlertAt: Instant = scheduledAt,
    ): ReminderState =
        ReminderState(
            occurrenceKey = key,
            medicationId = "m1",
            profileId = "p1",
            scheduledAt = scheduledAt,
            phase = ReminderPhase.ACTIVE,
            snoozedUntil = null,
            snoozedUntilPlace = null,
            graceAnchor = scheduledAt,
            nagCount = nagCount,
            firstNotifiedAt = scheduledAt,
            lastAlertAt = lastAlertAt,
        )

    @Test
    fun `due occurrence creates active state and posts with sound`() {
        val t = NagStateMachine.reduce(null, due, scheduledAt, settings)

        assertThat(t.state?.phase).isEqualTo(ReminderPhase.ACTIVE)
        assertThat(t.state?.nagCount).isEqualTo(0)
        assertThat(t.effects)
            .containsExactly(NagEffect.PostReminder(alert = true), NagEffect.Reschedule)
            .inOrder()
    }

    @Test
    fun `duplicate due delivery is a no-op`() {
        val existing = activeState()
        val t = NagStateMachine.reduce(existing, due, scheduledAt.plusSeconds(1), settings)

        assertThat(t.state).isEqualTo(existing)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `nag tick re-alerts and increments the counter`() {
        val t = NagStateMachine.reduce(activeState(), NagEvent.NagTick, scheduledAt.plusSeconds(600), settings)

        assertThat(t.state?.nagCount).isEqualTo(1)
        assertThat(t.state?.lastAlertAt).isEqualTo(scheduledAt.plusSeconds(600))
        assertThat(t.effects).contains(NagEffect.PostReminder(alert = true))
    }

    @Test
    fun `nag tick past the grace window expires to missed`() {
        val pastGrace = scheduledAt.plusSeconds(3601)
        val t = NagStateMachine.reduce(activeState(), NagEvent.NagTick, pastGrace, settings)

        assertThat(t.state).isNull()
        assertThat(t.effects)
            .containsExactly(
                NagEffect.CancelReminder,
                NagEffect.FinalizeDose(DoseStatus.MISSED),
                NagEffect.PostMissedNotice,
                NagEffect.Reschedule,
            ).inOrder()
    }

    @Test
    fun `nag tick at the max count expires instead of re-alerting`() {
        val t =
            NagStateMachine.reduce(
                activeState(nagCount = 5),
                NagEvent.NagTick,
                scheduledAt.plusSeconds(600),
                settings,
            )

        assertThat(t.state).isNull()
        assertThat(t.effects).contains(NagEffect.FinalizeDose(DoseStatus.MISSED))
    }

    @Test
    fun `take finalizes decrements stock and cancels`() {
        val t = NagStateMachine.reduce(activeState(), NagEvent.Take, scheduledAt.plusSeconds(60), settings)

        assertThat(t.state).isNull()
        assertThat(t.effects)
            .containsExactly(
                NagEffect.FinalizeDose(DoseStatus.TAKEN),
                NagEffect.DecrementStock,
                NagEffect.CancelReminder,
                NagEffect.Reschedule,
            ).inOrder()
    }

    @Test
    fun `skip finalizes without touching stock`() {
        val t = NagStateMachine.reduce(activeState(), NagEvent.Skip, scheduledAt.plusSeconds(60), settings)

        assertThat(t.state).isNull()
        assertThat(t.effects).doesNotContain(NagEffect.DecrementStock)
        assertThat(t.effects).contains(NagEffect.FinalizeDose(DoseStatus.SKIPPED))
    }

    @Test
    fun `take without prior state still writes the log`() {
        val t = NagStateMachine.reduce(null, NagEvent.Take, scheduledAt, settings)

        assertThat(t.state).isNull()
        assertThat(t.effects).contains(NagEffect.FinalizeDose(DoseStatus.TAKEN))
    }

    @Test
    fun `snooze parks the reminder and cancels the notification`() {
        val now = scheduledAt.plusSeconds(120)
        val t =
            NagStateMachine.reduce(
                activeState(),
                NagEvent.Snooze(SnoozeTarget.ForMinutes(10)),
                now,
                settings,
            )

        assertThat(t.state?.phase).isEqualTo(ReminderPhase.SNOOZED)
        assertThat(t.state?.snoozedUntil).isEqualTo(now.plusSeconds(600))
        assertThat(t.effects).containsExactly(NagEffect.CancelReminder, NagEffect.Reschedule).inOrder()
    }

    @Test
    fun `snooze until a place has no expiry time and remembers the place`() {
        val t =
            NagStateMachine.reduce(
                activeState(),
                NagEvent.Snooze(SnoozeTarget.UntilPlace(PlaceId.HOME)),
                scheduledAt.plusSeconds(120),
                settings,
            )

        assertThat(t.state?.phase).isEqualTo(ReminderPhase.SNOOZED)
        assertThat(t.state?.snoozedUntil).isNull()
        assertThat(t.state?.snoozedUntilPlace).isEqualTo(PlaceId.HOME)
        assertThat(t.effects).containsExactly(NagEffect.CancelReminder, NagEffect.Reschedule).inOrder()
    }

    @Test
    fun `reaching the place re-alerts and restarts the grace window`() {
        val snoozed =
            activeState(nagCount = 3).copy(
                phase = ReminderPhase.SNOOZED,
                snoozedUntil = null,
                snoozedUntilPlace = PlaceId.HOME,
            )
        // Hours later — way past the original grace window.
        val arrival = scheduledAt.plusSeconds(4 * 3600L)

        val t = NagStateMachine.reduce(snoozed, NagEvent.PlaceReached(PlaceId.HOME), arrival, settings)

        assertThat(t.state?.phase).isEqualTo(ReminderPhase.ACTIVE)
        assertThat(t.state?.snoozedUntilPlace).isNull()
        assertThat(t.state?.graceAnchor).isEqualTo(arrival)
        assertThat(t.state?.nagCount).isEqualTo(0)
        assertThat(t.effects).contains(NagEffect.PostReminder(alert = true))

        // The following nag tick must NOT expire it: grace counts from arrival now.
        val tick = NagStateMachine.reduce(t.state, NagEvent.NagTick, arrival.plusSeconds(600), settings)
        assertThat(tick.state).isNotNull()
        assertThat(tick.effects).contains(NagEffect.PostReminder(alert = true))
    }

    @Test
    fun `reaching a different place is a no-op`() {
        val snoozed =
            activeState().copy(
                phase = ReminderPhase.SNOOZED,
                snoozedUntil = null,
                snoozedUntilPlace = PlaceId.WORK,
            )
        val t =
            NagStateMachine.reduce(
                snoozed,
                NagEvent.PlaceReached(PlaceId.HOME),
                scheduledAt.plusSeconds(600),
                settings,
            )

        assertThat(t.state).isEqualTo(snoozed)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `time expiry does not wake a place snooze`() {
        val snoozed =
            activeState().copy(
                phase = ReminderPhase.SNOOZED,
                snoozedUntil = null,
                snoozedUntilPlace = PlaceId.HOME,
            )
        val t = NagStateMachine.reduce(snoozed, NagEvent.SnoozeExpired, scheduledAt.plusSeconds(600), settings)

        assertThat(t.state).isEqualTo(snoozed)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `dismiss re-posts silently and keeps the state untouched`() {
        val state = activeState(nagCount = 2)
        val t = NagStateMachine.reduce(state, NagEvent.Dismissed, scheduledAt.plusSeconds(300), settings)

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).containsExactly(NagEffect.PostReminder(alert = false))
    }

    @Test
    fun `dismiss while snoozed is a no-op`() {
        val snoozed = activeState().copy(phase = ReminderPhase.SNOOZED, snoozedUntil = scheduledAt.plusSeconds(600))
        val t = NagStateMachine.reduce(snoozed, NagEvent.Dismissed, scheduledAt.plusSeconds(300), settings)

        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `snooze expiry returns to active with an audible post`() {
        val snoozed =
            activeState(
                nagCount = 1,
            ).copy(phase = ReminderPhase.SNOOZED, snoozedUntil = scheduledAt.plusSeconds(600))
        val now = scheduledAt.plusSeconds(600)

        val t = NagStateMachine.reduce(snoozed, NagEvent.SnoozeExpired, now, settings)

        assertThat(t.state?.phase).isEqualTo(ReminderPhase.ACTIVE)
        assertThat(t.state?.snoozedUntil).isNull()
        assertThat(t.state?.nagCount).isEqualTo(1)
        assertThat(t.effects).contains(NagEffect.PostReminder(alert = true))
    }

    @Test
    fun `snooze expiry on an active state is a no-op`() {
        val state = activeState()
        val t = NagStateMachine.reduce(state, NagEvent.SnoozeExpired, scheduledAt.plusSeconds(600), settings)

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `grace expiry finalizes as missed from any phase`() {
        val snoozed = activeState().copy(phase = ReminderPhase.SNOOZED, snoozedUntil = scheduledAt.plusSeconds(7200))
        val t = NagStateMachine.reduce(snoozed, NagEvent.GraceExpired, scheduledAt.plusSeconds(3601), settings)

        assertThat(t.state).isNull()
        assertThat(t.effects).contains(NagEffect.FinalizeDose(DoseStatus.MISSED))
    }

    @Test
    fun `grace expiry without state is a no-op`() {
        val t = NagStateMachine.reduce(null, NagEvent.GraceExpired, scheduledAt, settings)

        assertThat(t.state).isNull()
        assertThat(t.effects).isEmpty()
    }
}
