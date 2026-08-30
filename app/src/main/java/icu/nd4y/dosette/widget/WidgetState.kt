package icu.nd4y.dosette.widget

import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.ScheduleType
import icu.nd4y.dosette.ui.today.DoseUiStatus
import icu.nd4y.dosette.ui.today.PrnMed
import icu.nd4y.dosette.ui.today.TodayDose
import icu.nd4y.dosette.ui.today.buildDayDoses
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot the widget renders; rebuilt on every widget update. */
data class WidgetState(
    val date: LocalDate,
    val doses: List<TodayDose> = emptyList(),
    val prn: List<PrnMed> = emptyList(),
    /** Minutes until the earliest pending dose; negative when it is already due. */
    val minutesToNext: Long? = null,
) {
    val taken: Int get() = doses.count { it.status == DoseUiStatus.TAKEN }
    val planned: Int get() = doses.size

    /** Earliest pending doses, all sharing the same time slot. */
    val nextSlotDoses: List<TodayDose>
        get() {
            val pending = doses.filter { it.status == DoseUiStatus.PENDING }
            val nextTime = pending.minOfOrNull { it.time } ?: return emptyList()
            return pending.filter { it.time == nextTime }
        }
}

/** Loads the same picture the Today screen shows, once, for the widget. */
@Singleton
class WidgetStateLoader
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val doseLogRepository: DoseLogRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) {
        suspend fun load(): WidgetState {
            val now = clock.instant()
            val date = now.atZone(clock.zone).toLocalDate()
            val profileId =
                settingsRepository.settings.first().activeProfileId
                    ?: return WidgetState(date)
            val meds = medicationRepository.observeByProfile(profileId).first()
            val logs = doseLogRepository.observeRange(profileId, date, date).first()
            val doses = buildDayDoses(date, meds, logs, clock.zone)

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

            val nextTime =
                doses
                    .filter { it.status == DoseUiStatus.PENDING }
                    .minOfOrNull { it.time }
            val minutesToNext =
                nextTime?.let {
                    Duration
                        .between(now, date.atTime(it).atZone(clock.zone).toInstant())
                        .toMinutes()
                }

            return WidgetState(
                date = date,
                doses = doses,
                prn = prn,
                minutesToNext = minutesToNext,
            )
        }
    }
