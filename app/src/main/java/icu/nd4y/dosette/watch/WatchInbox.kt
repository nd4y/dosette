package icu.nd4y.dosette.watch

import android.net.Uri
import android.util.Log
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.link.LinkPaths
import icu.nd4y.dosette.link.WatchAction
import icu.nd4y.dosette.link.WatchActionKind
import icu.nd4y.dosette.link.WatchJson
import icu.nd4y.dosette.reminders.PrnIntakes
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.UserDoseAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
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
    ) {
        private val mutex = Mutex()

        /** Applies the items in tap order. A malformed item is acknowledged too, or it would be retried forever. */
        suspend fun receive(items: List<PendingWatchAction>): Unit =
            mutex.withLock {
                val decoded = items.map { it to decode(it) }
                for ((item, action) in decoded.sortedBy { (_, action) -> action?.actedAt ?: Long.MAX_VALUE }) {
                    val applied =
                        action == null ||
                            runCatching { apply(action, item.uri) }
                                .onFailure {
                                    if (it is CancellationException) throw it
                                    Log.e(TAG, "watch action ${action.kind} failed", it)
                                }.isSuccess
                    // A failed apply keeps the item for the next drain.
                    if (applied) link.acknowledge(item.uri)
                }
            }

        private fun decode(item: PendingWatchAction): WatchAction? =
            WatchJson.decodeAction(item.json)?.takeIf {
                Uri.parse(item.uri).path == LinkPaths.actionPath(it.id)
            }

        /** Everything still queued — after a start or a reboot, when a delivery may have found the database locked. */
        suspend fun drain() {
            receive(link.pendingActions())
        }

        private suspend fun apply(
            action: WatchAction,
            sourceUri: String,
        ) {
            // An unknown or missing key is nothing to apply, not an error.
            val key = doseKey(action)
            val deliveryId = "watch-" + UUID.nameUUIDFromBytes(sourceUri.toByteArray(Charsets.UTF_8))
            when (action.kind) {
                WatchActionKind.TAKE, WatchActionKind.SKIP, WatchActionKind.SNOOZE -> {
                    val userAction =
                        when (action.kind) {
                            WatchActionKind.TAKE -> UserDoseAction.TAKE
                            WatchActionKind.SKIP -> UserDoseAction.SKIP
                            else -> UserDoseAction.SNOOZE
                        }
                    if (key != null) {
                        engine.onWatchAction(key, userAction, Instant.ofEpochMilli(action.actedAt), deliveryId)
                    }
                }

                WatchActionKind.TAKE_PRN -> {
                    action.medicationId?.let {
                        // Persist delivery identity with the log and stock change,
                        // even if acknowledgement fails after the transaction.
                        prnIntakes.take(it, Instant.ofEpochMilli(action.actedAt), deliveryId, deliveryId = deliveryId)
                    }
                }
            }
        }

        private fun doseKey(action: WatchAction): OccurrenceKey? =
            action.doseId?.let { encoded -> runCatching { OccurrenceKey.decode(encoded) }.getOrNull() }

        private companion object {
            const val TAG = "WatchInbox"
        }
    }
