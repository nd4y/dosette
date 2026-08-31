package icu.nd4y.dosette.reminders

import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.repository.PrnIntake
import icu.nd4y.dosette.data.repository.PrnRecorder
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.inventory.InventoryPolicy
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import kotlinx.coroutines.flow.first
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
        private val widgetRefresher: WidgetRefresher,
    ) {
        suspend fun take(medicationId: String): PrnIntake? {
            val med = medicationRepository.getDetails(medicationId) ?: return null
            val variant = med.defaultVariant?.takeIf { it.trackingEnabled }
            val before = variant?.let { medicationRepository.getVariant(it.id)?.currentStock }
            val intake = prnRecorder.record(medicationId)
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
                widgetRefresher.refresh()
            }
            return intake
        }

        /** Snackbar undo of [take]: removes the log and returns the stock. */
        suspend fun undo(logId: String) {
            doseLogRepository.undoPrn(logId)
            widgetRefresher.refresh()
        }
    }
