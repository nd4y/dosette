package icu.nd4y.dosette.reminders

import icu.nd4y.dosette.data.repository.AppointmentRepository
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.repository.ProfileRepository
import icu.nd4y.dosette.data.repository.ReminderStateRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.alarm.AlarmObligations
import icu.nd4y.dosette.domain.alarm.AlarmPlanner
import icu.nd4y.dosette.domain.inventory.InventoryPolicy
import icu.nd4y.dosette.domain.missed.MissedDosePolicy
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import icu.nd4y.dosette.domain.nag.NagEffect
import icu.nd4y.dosette.domain.nag.NagEvent
import icu.nd4y.dosette.domain.nag.NagSettings
import icu.nd4y.dosette.domain.nag.NagStateMachine
import icu.nd4y.dosette.domain.nag.SnoozeTarget
import icu.nd4y.dosette.domain.schedule.Occurrence
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import icu.nd4y.dosette.reminders.notifications.ReminderPayload
import icu.nd4y.dosette.reminders.places.PlaceMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class UserDoseAction { TAKE, SKIP, SNOOZE }

/**
 * Side-effect orchestrator of the reminder subsystem. Loads the world,
 * feeds events into the pure [NagStateMachine], executes the returned
 * effects and keeps the single [AlarmScheduler] alarm pointed at the
 * next obligation. Every entry point is serialized by a mutex — alarm
 * fires, notification actions and boot recovery never interleave.
 */
@Singleton
class ReminderEngine
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val doseLogRepository: DoseLogRepository,
        private val reminderStateRepository: ReminderStateRepository,
        private val appointmentRepository: AppointmentRepository,
        private val settingsRepository: SettingsRepository,
        private val profileRepository: ProfileRepository,
        private val notifier: ReminderNotifier,
        private val alarmScheduler: AlarmScheduler,
        private val placeMonitor: PlaceMonitor,
        private val widgetRefresher: WidgetRefresher,
        private val clock: Clock,
    ) {
        private val mutex = Mutex()

        /** Alarm fired (or app came to foreground): handle everything due, then re-arm. */
        suspend fun processDueEvents(): Unit =
            mutex.withLock {
                processDueLocked()
            }

        /** Take / Skip / Snooze from the notification or the app. */
        suspend fun onUserAction(
            key: OccurrenceKey,
            action: UserDoseAction,
        ): Unit =
            mutex.withLock {
                val world = loadWorld()
                val event =
                    when (action) {
                        UserDoseAction.TAKE -> {
                            NagEvent.Take
                        }

                        UserDoseAction.SKIP -> {
                            NagEvent.Skip
                        }

                        UserDoseAction.SNOOZE -> {
                            NagEvent.Snooze(SnoozeTarget.ForMinutes(world.settings.snoozeMin))
                        }
                    }
                applyEvent(world, key, event)
                syncGeofences(world)
                rescheduleLocked(world)
            }

        /** Snooze with an explicit target (duration or place) from the app UI. */
        suspend fun snooze(
            key: OccurrenceKey,
            target: SnoozeTarget,
        ): Unit =
            mutex.withLock {
                val world = loadWorld()
                applyEvent(world, key, NagEvent.Snooze(target))
                syncGeofences(world)
                rescheduleLocked(world)
            }

        /**
         * Revert an accidental Take/Skip mark: the stock consumed by the log
         * is returned and the log is deleted, so the occurrence is pending
         * again. The catch-up pass right after decides what that means now —
         * inside the grace window the reminder comes back (audibly), past it
         * the dose is quietly finalized as missed.
         */
        suspend fun undoDose(key: OccurrenceKey): Unit =
            mutex.withLock {
                val log = doseLogRepository.getScheduled(key) ?: return
                medicationRepository.restoreStockOf(log)
                doseLogRepository.delete(log.id)
                processDueLocked()
            }

        /**
         * Delete a one-off dose (a single-day schedule created from the
         * calendar) together with everything it produced: reminder state,
         * notification, logs — with stock returned for a taken one.
         */
        suspend fun deleteOneOffSchedule(
            medicationId: String,
            scheduleId: String,
        ): Unit =
            mutex.withLock {
                val med = medicationRepository.getDetails(medicationId)
                val schedule = med?.schedules?.firstOrNull { it.id == scheduleId }
                if (schedule != null) {
                    val end = schedule.endDate ?: schedule.startDate
                    OccurrenceGenerator
                        .occurrencesInRange(listOf(schedule), schedule.startDate, end)
                        .forEach { occurrence ->
                            if (reminderStateRepository.get(occurrence.key) != null) {
                                reminderStateRepository.delete(occurrence.key)
                                notifier.cancelReminder(occurrence.key)
                            }
                            doseLogRepository.getScheduled(occurrence.key)?.let { log ->
                                medicationRepository.restoreStockOf(log)
                                doseLogRepository.delete(log.id)
                            }
                        }
                    medicationRepository.deleteSchedule(scheduleId)
                }
                processDueLocked()
            }

        /** Geofence fired: wake every reminder waiting for [place]. */
        suspend fun onPlaceReached(place: PlaceId): Unit =
            mutex.withLock {
                val world = loadWorld()
                world.states
                    .filter { it.snoozedUntilPlace == place }
                    .forEach { state -> applyEvent(world, state.occurrenceKey, NagEvent.PlaceReached(place)) }
                syncGeofences(world)
                rescheduleLocked(world)
            }

        /** deleteIntent fired: the user swiped the ongoing reminder away. */
        suspend fun onDismissed(key: OccurrenceKey): Unit =
            mutex.withLock {
                val world = loadWorld()
                applyEvent(world, key, NagEvent.Dismissed)
                // No replan: a dismiss changes nothing about future obligations.
            }

        /** Boot / app update: re-post notifications for persisted states, then catch up. */
        suspend fun reconcile(): Unit =
            mutex.withLock {
                val world = loadWorld()
                world.states
                    .filter { it.phase == ReminderPhase.ACTIVE }
                    .forEach { state ->
                        // Silent restore: any due nag tick right below re-alerts audibly.
                        notifier.postReminder(payloadFor(world, state.occurrenceKey), alert = false)
                    }
                processDueLocked(world)
            }

        /**
         * Data or settings changed. A full catch-up pass, not just re-arming:
         * an import or edit can introduce occurrences that are already due
         * (inside the grace window) and they must alert immediately — found
         * the hard way on-device, where an import whose times had all passed
         * produced no notifications until the nightly housekeeping tick.
         */
        suspend fun reschedule(): Unit =
            mutex.withLock {
                processDueLocked()
            }

        private suspend fun processDueLocked(preloaded: World? = null) {
            val world = preloaded ?: loadWorld()
            val now = clock.instant()
            val slackEnd = now.plus(SLACK)

            handleDueOccurrences(world, now, slackEnd)
            handleStates(world, now, slackEnd)
            handleAppointments(world, now, slackEnd)
            rescheduleLocked(world)
        }

        private suspend fun handleDueOccurrences(
            world: World,
            now: Instant,
            slackEnd: Instant,
        ) {
            val zone = clock.zone
            val today = now.atZone(zone).toLocalDate()
            val from = today.minusDays(MISSED_SWEEP_DAYS)
            val logged =
                doseLogRepository
                    .getScheduledInRange(from, today)
                    .map { OccurrenceKey(it.medicationId, it.date, requireNotNull(it.time)) }
                    .toSet()

            for (med in world.medications) {
                val schedules = med.schedules.filter { it.remindersEnabled }
                val occurrences = OccurrenceGenerator.occurrencesInRange(schedules, from, today)
                for (occurrence in occurrences) {
                    val instant = occurrence.instantAt(zone)
                    val actionable =
                        !instant.isAfter(slackEnd) &&
                            occurrence.key !in logged &&
                            !world.stateByKey.containsKey(occurrence.key)
                    if (!actionable) continue

                    if (MissedDosePolicy.isMissed(instant, now, world.settings.missedGraceMin)) {
                        // Quiet finalization: the reminder window is long gone.
                        doseLogRepository.recordScheduledIfAbsent(
                            buildLog(med, occurrence, instant, DoseStatus.MISSED, actedAt = null),
                        )
                    } else {
                        applyEvent(
                            world,
                            occurrence.key,
                            NagEvent.OccurrenceDue(
                                key = occurrence.key,
                                medicationId = med.medication.id,
                                profileId = med.medication.profileId,
                                scheduledAt = instant,
                            ),
                        )
                    }
                }
            }
        }

        private suspend fun handleStates(
            world: World,
            now: Instant,
            slackEnd: Instant,
        ) {
            for (state in world.states) {
                // Orphaned state: its medication is gone (profile removal,
                // med deletion, import) or archived — cancel it instead of
                // nagging forever with a raw UUID as the title.
                if (world.medicationById[state.occurrenceKey.medicationId] == null) {
                    reminderStateRepository.delete(state.occurrenceKey)
                    world.stateByKey.remove(state.occurrenceKey)
                    notifier.cancelReminder(state.occurrenceKey)
                    continue
                }
                val graceEnd =
                    state.graceAnchor.plus(Duration.ofMinutes(world.settings.missedGraceMin.toLong()))
                when (state.phase) {
                    ReminderPhase.ACTIVE -> {
                        // The nag-budget guard mirrors AlarmPlanner: once nags
                        // are exhausted the planner stops scheduling ticks, and
                        // a pass triggered by anything else must not deliver
                        // the tick that would finalize the dose early.
                        val tickDue =
                            world.settings.nagIntervalMin > 0 &&
                                state.nagCount + 1 < world.settings.nagMaxCount &&
                                !state.lastAlertAt
                                    .plus(Duration.ofMinutes(world.settings.nagIntervalMin.toLong()))
                                    .isAfter(slackEnd)
                        when {
                            now.isAfter(graceEnd) -> applyEvent(world, state.occurrenceKey, NagEvent.GraceExpired)
                            tickDue -> applyEvent(world, state.occurrenceKey, NagEvent.NagTick)
                        }
                    }

                    ReminderPhase.SNOOZED -> {
                        handleSnoozedState(world, state, slackEnd)
                    }
                }
            }
            syncGeofences(world)
        }

        private suspend fun handleSnoozedState(
            world: World,
            state: ReminderState,
            slackEnd: Instant,
        ) {
            val place = state.snoozedUntilPlace
            if (place != null) {
                // No time expiry while waiting for a place; the Wi-Fi check
                // is the fallback for a missed geofence, and a place cleared
                // in settings after the snooze can never signal — reactivate
                // then instead of polling forever.
                val config = world.places[place]?.takeIf { it.isConfigured }
                if (config == null || placeMonitor.isCurrentlyAt(config)) {
                    applyEvent(world, state.occurrenceKey, NagEvent.PlaceReached(place))
                }
            } else {
                // Grace deliberately does not end a snooze early: waking from
                // one restarts the grace window (NagStateMachine.onSnoozeExpired),
                // so only the expiry itself matters here.
                val snoozeOver = state.snoozedUntil?.isAfter(slackEnd) == false
                if (snoozeOver) applyEvent(world, state.occurrenceKey, NagEvent.SnoozeExpired)
            }
        }

        /** Keep geofences registered for exactly the places reminders wait on. */
        private fun syncGeofences(world: World) {
            val waiting =
                world.stateByKey.values
                    .mapNotNull { it.snoozedUntilPlace }
                    .toSet()
                    .mapNotNull { place ->
                        world.places[place]?.takeIf { it.isConfigured }?.let { place to it }
                    }.toMap()
            placeMonitor.syncGeofences(waiting)
        }

        private suspend fun handleAppointments(
            world: World,
            now: Instant,
            slackEnd: Instant,
        ) {
            val zone = clock.zone
            val windowStart = now.minus(APPOINTMENT_WINDOW)
            var posted = false
            for (appointment in world.appointments) {
                val startsAt =
                    appointment.date
                        .atTime(appointment.time)
                        .atZone(zone)
                        .toInstant()
                for (offset in appointment.reminderOffsetsMin) {
                    val remindAt = startsAt.minus(Duration.ofMinutes(offset.toLong()))
                    val fresh = world.appointmentSweepMark?.isBefore(remindAt) != false
                    if (fresh && remindAt.isAfter(windowStart) && !remindAt.isAfter(slackEnd)) {
                        notifier.postAppointment(appointment, offset)
                        posted = true
                    }
                }
            }
            // The watermark keeps later passes inside the window from
            // re-posting (and re-sounding) the same reminder — a dismissed
            // appointment notice stays dismissed.
            if (posted) settingsRepository.setLastAppointmentSweepAt(slackEnd)
        }

        private suspend fun applyEvent(
            world: World,
            key: OccurrenceKey,
            event: NagEvent,
        ) {
            val now = clock.instant()
            val state = world.stateByKey[key] ?: reminderStateRepository.get(key)
            val transition = NagStateMachine.reduce(state, event, now, world.settings)

            if (transition.state != null) {
                reminderStateRepository.upsert(transition.state)
                world.stateByKey[key] = transition.state
            } else if (state != null) {
                reminderStateRepository.delete(key)
                world.stateByKey.remove(key)
            }

            val med = world.medicationById[key.medicationId] ?: medicationRepository.getDetails(key.medicationId)
            // The existing log guards the stock effects below: whether this
            // occurrence was already counted as taken decides if a decrement
            // (or a restore before an overwrite) is due.
            val priorLog =
                if (transition.effects.any { it is NagEffect.FinalizeDose || it == NagEffect.DecrementStock }) {
                    doseLogRepository.getScheduled(key)
                } else {
                    null
                }
            val prior = Prior(state, priorLog)
            for (effect in transition.effects) {
                executeEffect(world, key, med, prior, effect)
            }
        }

        private suspend fun executeEffect(
            world: World,
            key: OccurrenceKey,
            med: MedicationDetails?,
            prior: Prior,
            effect: NagEffect,
        ) {
            when (effect) {
                is NagEffect.PostReminder -> {
                    notifier.postReminder(payloadFor(world, key), effect.alert)
                }

                NagEffect.CancelReminder -> {
                    notifier.cancelReminder(key)
                }

                is NagEffect.FinalizeDose -> {
                    if (med != null) finalizeFromEffect(key, med, prior, effect.status)
                }

                NagEffect.DecrementStock -> {
                    // Already counted as taken: a repeated Take (double tap,
                    // queued broadcast) must not drain the stock again.
                    if (prior.log?.status != DoseStatus.TAKEN) decrementStock(world, med, key)
                }

                NagEffect.PostMissedNotice -> {
                    notifier.postMissedNotice(key, med?.let(::medicationTitle) ?: key.medicationId)
                }

                NagEffect.Reschedule -> {
                    Unit
                } // Batched: the caller re-arms once at the end.
            }
        }

        private suspend fun finalizeFromEffect(
            key: OccurrenceKey,
            med: MedicationDetails,
            prior: Prior,
            status: DoseStatus,
        ) {
            // No occurrence — the schedule was deleted or replaced under a
            // live state; nothing to record. A log already in this status is
            // left alone so its actedAt survives repeated delivery.
            val occurrence = occurrenceFor(med, key) ?: return
            if (prior.log?.status == status) return
            // Flipping an already-taken dose away from TAKEN returns the
            // stock its log consumed before the row is overwritten (and its
            // consumed units are lost).
            if (prior.log?.status == DoseStatus.TAKEN) medicationRepository.restoreStockOf(prior.log)
            val scheduledAt =
                prior.state?.scheduledAt ?: key.date
                    .atTime(key.time)
                    .atZone(clock.zone)
                    .toInstant()
            val actedAt = if (status == DoseStatus.MISSED) null else clock.instant()
            doseLogRepository.finalizeScheduled(
                buildLog(med, occurrence, scheduledAt, status, actedAt),
            )
        }

        private suspend fun decrementStock(
            world: World,
            med: MedicationDetails?,
            key: OccurrenceKey,
        ) {
            val variant = med?.defaultVariant?.takeIf { it.trackingEnabled } ?: return
            // The amount of THIS slot, not today's first one: slots can carry
            // different doses, and the decrement must match the log.
            val amount = occurrenceFor(med, key)?.amount ?: med.schedules.firstOrNull()?.defaultDoseAmount ?: 1.0
            val units =
                InventoryPolicy.unitsForDose(amount, med.medication.strengthValue, variant.strengthValue)
            val before = medicationRepository.getVariant(variant.id)?.currentStock ?: return
            medicationRepository.decrementStock(variant.id, units)
            val after = (before - units).coerceAtLeast(0.0)
            if (world.lowStockNotifyEnabled &&
                InventoryPolicy.crossedLowThreshold(before, after, variant.lowStockThreshold)
            ) {
                notifier.postLowStock(med.medication.id, medicationTitle(med), formatUnits(after))
            }
        }

        private fun buildLog(
            med: MedicationDetails,
            occurrence: Occurrence,
            scheduledAt: Instant,
            status: DoseStatus,
            actedAt: Instant?,
        ): DoseLog {
            val key = occurrence.key
            val amount = occurrence.amount
            val variant = if (status == DoseStatus.TAKEN) med.defaultVariant else null
            val consumed =
                variant?.let {
                    InventoryPolicy.unitsForDose(amount, med.medication.strengthValue, it.strengthValue)
                }
            return DoseLog(
                id = UUID.randomUUID().toString(),
                profileId = med.medication.profileId,
                medicationId = med.medication.id,
                scheduleId = occurrence.scheduleId,
                kind = DoseKind.SCHEDULED,
                date = key.date,
                time = key.time,
                scheduledAt = scheduledAt,
                status = status,
                actedAt = actedAt,
                amount = amount,
                variantId = variant?.id,
                consumedUnits = consumed,
                note = null,
                updatedAt = clock.instant(),
            )
        }

        /** Occurrence matching [key], or null when its schedule no longer produces it. */
        private fun occurrenceFor(
            med: MedicationDetails,
            key: OccurrenceKey,
        ): Occurrence? =
            OccurrenceGenerator
                .occurrencesOn(med.schedules, key.date)
                .firstOrNull { it.time == key.time }

        private suspend fun rescheduleLocked(world: World) {
            val settings = world.settings
            val schedules =
                medicationRepository
                    .getAllActive()
                    .flatMap { it.schedules }
                    .filter { it.remindersEnabled }
            val states = reminderStateRepository.getAll()
            val today = clock.instant().atZone(clock.zone).toLocalDate()
            val appointments = appointmentRepository.getAllFrom(today)
            val plan =
                AlarmPlanner.nextAlarm(
                    clock.instant(),
                    clock.zone,
                    AlarmObligations(schedules, states, appointments),
                    settings,
                )
            alarmScheduler.scheduleExact(plan.at, alarmClock = world.alarmClock)
            // Every mutating entry point ends here, so the widget stays in step.
            widgetRefresher.refresh()
        }

        private fun payloadFor(
            world: World,
            key: OccurrenceKey,
        ): ReminderPayload {
            val med = world.medicationById[key.medicationId]
            return ReminderPayload(
                key = key,
                title = med?.let(::medicationTitle) ?: key.medicationId,
                amountText = med?.let { occurrenceFor(it, key)?.amount }?.let(::formatUnits),
                instructions = med?.medication?.instructions,
                profileName =
                    if (world.multiProfile) {
                        world.profileNames[med?.medication?.profileId]
                    } else {
                        null
                    },
            )
        }

        private suspend fun loadWorld(): World {
            val settings = settingsRepository.settings.first()
            val medications = medicationRepository.getAllActive()
            val profiles = profileRepository.getAll()
            val states = reminderStateRepository.getAll()
            val today = clock.instant().atZone(clock.zone).toLocalDate()
            return World(
                settings =
                    NagSettings(
                        nagIntervalMin = settings.nagIntervalMin,
                        nagMaxCount = settings.nagMaxCount,
                        snoozeMin = settings.snoozeMin,
                        missedGraceMin = settings.missedGraceMin,
                    ),
                lowStockNotifyEnabled = settings.lowStockNotifyEnabled,
                alarmClock = settings.alarmClock,
                places = settings.places,
                appointmentSweepMark = settings.lastAppointmentSweepAt,
                medications = medications,
                medicationById = medications.associateBy { it.medication.id },
                states = states,
                stateByKey = states.associateBy { it.occurrenceKey }.toMutableMap(),
                appointments = appointmentRepository.getAllFrom(today),
                profileNames = profiles.associate { it.id to it.name },
                multiProfile = profiles.size > 1,
            )
        }

        /** What was true for the occurrence before this event's effects run. */
        private data class Prior(
            val state: ReminderState?,
            val log: DoseLog?,
        )

        private data class World(
            val settings: NagSettings,
            val lowStockNotifyEnabled: Boolean,
            /** Alarm flavour, see [AlarmScheduler.scheduleExact]. */
            val alarmClock: Boolean,
            val places: Map<PlaceId, PlaceConfig>,
            /** Appointment reminders up to this instant were already posted. */
            val appointmentSweepMark: Instant?,
            val medications: List<MedicationDetails>,
            val medicationById: Map<String, MedicationDetails>,
            val states: List<ReminderState>,
            val stateByKey: MutableMap<OccurrenceKey, ReminderState>,
            val appointments: List<icu.nd4y.dosette.domain.model.Appointment>,
            val profileNames: Map<String, String>,
            val multiProfile: Boolean,
        )

        private companion object {
            val SLACK: Duration = Duration.ofSeconds(30)
            val APPOINTMENT_WINDOW: Duration = Duration.ofMinutes(5)
            const val MISSED_SWEEP_DAYS = 7L
        }
    }

/** Returns the stock a taken dose's log consumed (no-op for other logs). */
private suspend fun MedicationRepository.restoreStockOf(log: DoseLog) {
    if (log.status == DoseStatus.TAKEN && log.variantId != null && log.consumedUnits != null) {
        incrementStock(log.variantId, log.consumedUnits)
    }
}

internal fun medicationTitle(med: MedicationDetails): String {
    val strength =
        med.medication.strengthValue?.let { value ->
            "${formatUnits(value)} ${med.medication.strengthUnit.orEmpty()}".trim()
        }
    return listOfNotNull(med.medication.name, strength).joinToString(" ")
}

internal fun formatUnits(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
