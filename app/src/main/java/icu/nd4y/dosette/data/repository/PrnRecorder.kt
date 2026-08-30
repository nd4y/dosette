package icu.nd4y.dosette.data.repository

import icu.nd4y.dosette.domain.inventory.InventoryPolicy
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.ScheduleType
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One recorded as-needed intake, ready to be offered for undo. */
data class PrnIntake(
    val logId: String,
    val medicationName: String,
)

/**
 * Records an as-needed intake: writes the PRN log and lets the repository
 * decrement the default variant's stock. Shared by the Today screen and
 * the home-screen widget.
 */
@Singleton
class PrnRecorder
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val doseLogRepository: DoseLogRepository,
        private val clock: Clock,
    ) {
        /** @return the recorded intake, or null when the medication has no open PRN schedule. */
        suspend fun record(medicationId: String): PrnIntake? {
            val med = medicationRepository.getDetails(medicationId)
            val schedule =
                med?.schedules?.firstOrNull { it.endDate == null && it.type == ScheduleType.AS_NEEDED }
            if (med == null || schedule == null) return null
            val variant = med.defaultVariant
            val amount = schedule.defaultDoseAmount
            val consumed =
                variant?.let {
                    InventoryPolicy.unitsForDose(amount, med.medication.strengthValue, it.strengthValue)
                }
            val now = clock.instant()
            val logId = UUID.randomUUID().toString()
            doseLogRepository.recordPrn(
                DoseLog(
                    id = logId,
                    profileId = med.medication.profileId,
                    medicationId = med.medication.id,
                    scheduleId = schedule.id,
                    kind = DoseKind.PRN,
                    date = now.atZone(clock.zone).toLocalDate(),
                    time = null,
                    scheduledAt = null,
                    status = DoseStatus.TAKEN,
                    actedAt = now,
                    amount = amount,
                    variantId = variant?.id,
                    consumedUnits = consumed,
                    note = null,
                    updatedAt = now,
                ),
            )
            return PrnIntake(logId = logId, medicationName = med.medication.name)
        }
    }
