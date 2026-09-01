package icu.nd4y.dosette.widget

import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationDetails
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.PrnMed
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.ui.today.buildDayDoses
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot the widget renders; rebuilt on every widget update. */
data class WidgetState(
    val date: LocalDate,
    val doses: List<TodayDose> = emptyList(),
    /** Yesterday's still-unresolved doses (a snooze across midnight). */
    val carryover: List<TodayDose> = emptyList(),
    val prn: List<PrnMed> = emptyList(),
    /** Minutes until the earliest pending dose; negative when it is already due. */
    val minutesToNext: Long? = null,
) {
    /** The ring stays a picture of TODAY; carryover is listed, not counted. */
    val taken: Int get() = doses.count { it.status == DoseUiStatus.TAKEN }
    val planned: Int get() = doses.size

    /** Earliest pending doses, all sharing the same day and time slot. */
    val nextSlotDoses: List<TodayDose>
        get() {
            val pending = carryover + doses.filter { it.status == DoseUiStatus.PENDING }
            val next =
                pending.minWithOrNull(compareBy({ it.date }, { it.time })) ?: return emptyList()
            return pending.filter { it.date == next.date && it.time == next.time }
        }
}

/**
 * The same picture the Today screen shows, as a flow: a Glance session
 * stays alive for a while after an update, so the widget collects live
 * data instead of a one-shot snapshot — otherwise a take from the widget
 * itself would not be reflected until the session expires.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WidgetStateLoader
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val doseLogRepository: DoseLogRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) {
        fun observe(): Flow<WidgetState> =
            settingsRepository.settings
                .map { it.activeProfileId }
                .distinctUntilChanged()
                .flatMapLatest { profileId ->
                    // The same date for the log query and the render: mixing a
                    // captured date with a re-read one around midnight would
                    // pair the new day's doses with the old day's logs.
                    val date = today()
                    if (profileId == null) {
                        flowOf(WidgetState(date))
                    } else {
                        combine(
                            medicationRepository.observeByProfile(profileId),
                            // Yesterday too: a dose snoozed across midnight is
                            // still the most urgent thing the widget can show.
                            doseLogRepository.observeRange(profileId, date.minusDays(1), date),
                        ) { meds, logs -> build(date, meds, logs) }
                    }
                }

        suspend fun load(): WidgetState = observe().first()

        private fun today() = clock.instant().atZone(clock.zone).toLocalDate()

        private fun build(
            date: LocalDate,
            meds: List<MedicationDetails>,
            logs: List<DoseLog>,
        ): WidgetState {
            val now = clock.instant()
            val doses = buildDayDoses(date, meds, logs, clock.zone)
            val carryover =
                buildDayDoses(date.minusDays(1), meds, logs, clock.zone)
                    .filter { it.status == DoseUiStatus.PENDING }

            val prn =
                meds
                    .filter { details -> !details.medication.isArchived }
                    .filter { details ->
                        details.schedules.any { it.endDate == null && it.type == ScheduleType.AS_NEEDED }
                    }.map { details ->
                        PrnMed(
                            medicationId = details.medication.id,
                            name = details.medication.name,
                            strengthText = null,
                            form = details.medication.form,
                            colorSeed = details.medication.colorSeed,
                        )
                    }

            val next =
                (carryover + doses.filter { it.status == DoseUiStatus.PENDING })
                    .minWithOrNull(compareBy({ it.date }, { it.time }))
            val minutesToNext =
                next?.let {
                    Duration
                        .between(
                            now,
                            it.date
                                .atTime(it.time)
                                .atZone(clock.zone)
                                .toInstant(),
                        ).toMinutes()
                }

            return WidgetState(
                date = date,
                doses = doses,
                carryover = carryover,
                prn = prn,
                minutesToNext = minutesToNext,
            )
        }
    }
