package icu.nd4y.dosette.reminders

import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.repository.PrnIntake
import icu.nd4y.dosette.data.repository.PrnRecorder
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.inventory.InventoryPolicy
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * As-needed intakes from the Today screen and the widget: the record
 * itself, the same low-stock policy a scheduled Take applies, and the
 * widget refresh. No alarms are involved, so this lives outside the
 * [ReminderEngine] mutex.
 */
@Singleton
class PrnIntakes
    @Inject
    constructor(
        private val prnRecorder: PrnRecorder,
        private val medicationRepository: MedicationRepository,
        private val doseLogRepository: DoseLogRepository,
        private val settingsRepository: SettingsRepository,
        private val notifier: ReminderNotifier,
        private val mirrorRefresher: MirrorRefresher,
    ) {
        suspend fun take(
            medicationId: String,
            actedAt: Instant? = null,
            logId: String = UUID.randomUUID().toString(),
            deliveryId: String? = null,
        ): PrnIntake? {
            val med = medicationRepository.getDetails(medicationId) ?: return null
            val variant = med.defaultVariant?.takeIf { it.trackingEnabled }
            val before = variant?.let { medicationRepository.getVariant(it.id)?.currentStock }
            val intake =
                if (actedAt == null) {
                    prnRecorder.record(medicationId, logId = logId, deliveryId = deliveryId)
                } else {
                    prnRecorder.record(medicationId, actedAt, logId, deliveryId)
                }
            if (intake != null) {
                if (variant != null && before != null) {
                    val after = medicationRepository.getVariant(variant.id)?.currentStock ?: before
                    val settings = settingsRepository.settings.first()
                    if (settings.lowStockNotifyEnabled &&
                        InventoryPolicy.crossedLowThreshold(before, after, variant.lowStockThreshold)
                    ) {
                        notifier.postLowStock(med.medication.id, medicationTitle(med), formatUnits(after))
                    }
                }
            }
            // A previous delivery may have committed, then failed while
            // publishing its snapshot. Retrying still refreshes the watch.
            if (intake != null || deliveryId != null) mirrorRefresher.refresh()
            return intake
        }

        /** Snackbar undo of [take]: removes the log and returns the stock. */
        suspend fun undo(logId: String) {
            doseLogRepository.undoPrn(logId)
            mirrorRefresher.refresh()
        }
    }
