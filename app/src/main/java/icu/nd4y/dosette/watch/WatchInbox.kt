package icu.nd4y.dosette.watch

import android.util.Log
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.link.WatchAction
import icu.nd4y.dosette.link.WatchActionKind
import icu.nd4y.dosette.link.WatchJson
import icu.nd4y.dosette.reminders.PrnIntakes
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.UserDoseAction
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies taps made on the watch. Each arrives as its own Data Layer item
 * and is deleted once applied, which is also how the watch learns the tap
 * went through. A take carries the moment of the tap: a watch that was out
 * of range for an hour must not record the dose as taken just now.
 */
@Singleton
class WatchInbox
    @Inject
    constructor(
        private val engine: ReminderEngine,
        private val prnIntakes: PrnIntakes,
        private val link: WatchLink,
        private val clock: Clock,
    ) {
        /** Applies the items in tap order. A malformed item is acknowledged too, or it would be retried forever. */
        suspend fun receive(items: List<PendingWatchAction>) {
            val decoded = items.map { it to WatchJson.decodeAction(it.json) }
            for ((item, action) in decoded.sortedBy { (_, action) -> action?.actedAt ?: Long.MAX_VALUE }) {
                val applied =
                    action == null ||
                        runCatching { apply(action) }
                            .onFailure { Log.e(TAG, "watch action ${action.kind} failed", it) }
                            .isSuccess
                // A failed apply keeps the item for the next drain.
                if (applied) link.acknowledge(item.uri)
            }
        }

        /** Everything still queued — after a start or a reboot, when a delivery may have found the database locked. */
        suspend fun drain() {
            receive(link.pendingActions())
        }

        private suspend fun apply(action: WatchAction) {
            // An unknown or missing key is nothing to apply, not an error.
            val key = doseKey(action)
            when (action.kind) {
                WatchActionKind.TAKE -> {
                    // Never in the future: the two clocks are not the same clock.
                    val actedAt = Instant.ofEpochMilli(action.actedAt).coerceAtMost(clock.instant())
                    if (key != null) engine.takeAt(key, actedAt)
                }

                WatchActionKind.SKIP -> {
                    if (key != null) engine.onUserAction(key, UserDoseAction.SKIP)
                }

                WatchActionKind.SNOOZE -> {
                    if (key != null) engine.onUserAction(key, UserDoseAction.SNOOZE)
                }

                WatchActionKind.TAKE_PRN -> {
                    action.medicationId?.let { prnIntakes.take(it) }
                }
            }
        }

        private fun doseKey(action: WatchAction): OccurrenceKey? =
            action.doseId?.let { encoded -> runCatching { OccurrenceKey.decode(encoded) }.getOrNull() }

        private companion object {
            const val TAG = "WatchInbox"
        }
    }
