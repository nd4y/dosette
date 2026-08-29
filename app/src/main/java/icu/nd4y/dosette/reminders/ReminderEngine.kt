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
import icu.nd4y.dosette.domain.model.ReminderPhase
import icu.nd4y.dosette.domain.model.ReminderState
import icu.nd4y.dosette.domain.nag.NagEffect
import icu.nd4y.dosette.domain.nag.NagEvent
import icu.nd4y.dosette.domain.nag.NagSettings
import icu.nd4y.dosette.domain.nag.NagStateMachine
import icu.nd4y.dosette.domain.schedule.Occurrence
import icu.nd4y.dosette.domain.schedule.OccurrenceGenerator
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import icu.nd4y.dosette.reminders.notifications.ReminderPayload
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
                        UserDoseAction.TAKE -> NagEvent.Take
                        UserDoseAction.SKIP -> NagEvent.Skip
                        UserDoseAction.SNOOZE -> NagEvent.Snooze
                    }
                applyEvent(world, key, event)
                rescheduleLocked(world.settings)
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
            rescheduleLocked(world.settings)
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
                val graceEnd =
                    state.scheduledAt.plus(Duration.ofMinutes(world.settings.missedGraceMin.toLong()))
                when (state.phase) {
                    ReminderPhase.ACTIVE -> {
                        val tickDue =
                            world.settings.nagIntervalMin > 0 &&
                                !state.lastAlertAt
                                    .plus(Duration.ofMinutes(world.settings.nagIntervalMin.toLong()))
                                    .isAfter(slackEnd)
                        when {
                            now.isAfter(graceEnd) -> applyEvent(world, state.occurrenceKey, NagEvent.GraceExpired)
                            tickDue -> applyEvent(world, state.occurrenceKey, NagEvent.NagTick)
                        }
                    }

                    ReminderPhase.SNOOZED -> {
                        val snoozeOver = state.snoozedUntil?.isAfter(slackEnd) == false
                        when {
                            now.isAfter(graceEnd) -> applyEvent(world, state.occurrenceKey, NagEvent.GraceExpired)
                            snoozeOver -> applyEvent(world, state.occurrenceKey, NagEvent.SnoozeExpired)
                        }
                    }
                }
            }
        }

        private fun handleAppointments(
            world: World,
            now: Instant,
            slackEnd: Instant,
        ) {
            val zone = clock.zone
            val windowStart = now.minus(APPOINTMENT_WINDOW)
            for (appointment in world.appointments) {
                val startsAt =
                    appointment.date
                        .atTime(appointment.time)
                        .atZone(zone)
                        .toInstant()
                for (offset in appointment.reminderOffsetsMin) {
                    val remindAt = startsAt.minus(Duration.ofMinutes(offset.toLong()))
                    if (remindAt.isAfter(windowStart) && !remindAt.isAfter(slackEnd)) {
                        notifier.postAppointment(appointment, offset)
                    }
                }
            }
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
            for (effect in transition.effects) {
                executeEffect(world, key, state, med, effect)
            }
        }

        private suspend fun executeEffect(
            world: World,
            key: OccurrenceKey,
            previousState: ReminderState?,
            med: MedicationDetails?,
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
                    if (med != null) {
                        val occurrence = occurrenceFor(med, key)
                        val scheduledAt =
                            previousState?.scheduledAt ?: key.date
                                .atTime(key.time)
                                .atZone(clock.zone)
                                .toInstant()
                        val actedAt = if (effect.status == DoseStatus.MISSED) null else clock.instant()
                        doseLogRepository.finalizeScheduled(
                            buildLog(med, occurrence, scheduledAt, effect.status, actedAt),
                        )
                    }
                }

                NagEffect.DecrementStock -> {
                    decrementStock(world, med)
                }

                NagEffect.PostMissedNotice -> {
                    notifier.postMissedNotice(key, med?.let(::medicationTitle) ?: key.medicationId)
                }

                NagEffect.Reschedule -> {
                    Unit
                } // Batched: the caller re-arms once at the end.
            }
        }

        private suspend fun decrementStock(
            world: World,
            med: MedicationDetails?,
        ) {
            val variant = med?.defaultVariant?.takeIf { it.trackingEnabled } ?: return
            val amount = occurrenceFor(med, null)?.amount ?: med.schedules.firstOrNull()?.defaultDoseAmount ?: 1.0
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
            occurrence: Occurrence?,
            scheduledAt: Instant,
            status: DoseStatus,
            actedAt: Instant?,
        ): DoseLog {
            val key =
                occurrence?.key ?: error("scheduled log needs an occurrence")
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

        /** Occurrence matching [key]; when key is null — today's first occurrence (dose amount lookup). */
        private fun occurrenceFor(
            med: MedicationDetails,
            key: OccurrenceKey?,
        ): Occurrence? {
            val date = key?.date ?: clock.instant().atZone(clock.zone).toLocalDate()
            val occurrences = OccurrenceGenerator.occurrencesOn(med.schedules, date)
            return if (key == null) {
                occurrences.firstOrNull()
            } else {
                occurrences.firstOrNull { it.time == key.time }
            }
        }

        private suspend fun rescheduleLocked(settings: NagSettings) {
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
            alarmScheduler.scheduleExact(plan.at)
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

        private fun medicationTitle(med: MedicationDetails): String {
            val strength =
                med.medication.strengthValue?.let { value ->
                    "${formatUnits(value)} ${med.medication.strengthUnit.orEmpty()}".trim()
                }
            return listOfNotNull(med.medication.name, strength).joinToString(" ")
        }

        private fun formatUnits(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

        private suspend fun loadWorld(): World {
            val settings = settingsRepository.settings.first()
            val medications = medicationRepository.getAllActive()
            val profiles = profileRepository.getAll()
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
                medications = medications,
                medicationById = medications.associateBy { it.medication.id },
                states = reminderStateRepository.getAll(),
                stateByKey =
                    reminderStateRepository
                        .getAll()
                        .associateBy { it.occurrenceKey }
                        .toMutableMap(),
                appointments = appointmentRepository.getAllFrom(today),
                profileNames = profiles.associate { it.id to it.name },
                multiProfile = profiles.size > 1,
            )
        }

        private data class World(
            val settings: NagSettings,
            val lowStockNotifyEnabled: Boolean,
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
