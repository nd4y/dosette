package icu.nd4y.dosette.data.repository

import androidx.room.withTransaction
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.entity.DoseActionCursorEntity
import icu.nd4y.dosette.data.db.entity.WatchActionReceiptEntity
import icu.nd4y.dosette.data.db.toMinutes
import icu.nd4y.dosette.domain.model.OccurrenceKey
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Database mutations, delivery receipts and per-dose intent order commit together. */
@Singleton
class WatchActionRepository
    @Inject
    constructor(
        private val db: AppDatabase,
    ) {
        suspend fun applyOnce(
            deliveryId: String,
            key: OccurrenceKey,
            actedAt: Instant,
            block: suspend () -> Unit,
        ): Unit =
            db.withTransaction {
                val receipts = db.watchActionReceiptDao()
                if (!receipts.contains(deliveryId)) {
                    val encoded = key.encode()
                    // Existing/imported user marks may predate the cursor table.
                    // Automatic MISSED records must still permit delayed takes.
                    val priorLog = db.doseLogDao().getScheduled(key.medicationId, key.date, key.time.toMinutes())
                    val priorIntent =
                        priorLog?.takeIf { it.status == "TAKEN" || it.status == "SKIPPED" }?.updatedAt?.toEpochMilli()
                    val latest = receipts.latestActionAt(encoded) ?: priorIntent
                    if (latest == null || actedAt.toEpochMilli() > latest) {
                        block()
                        receipts.upsertCursor(DoseActionCursorEntity(encoded, actedAt.toEpochMilli()))
                    }
                    // Also acknowledge stale intents, so retry cannot revive them.
                    receipts.insert(WatchActionReceiptEntity(deliveryId))
                }
            }

        /** Phone actions always win locally; their intent time is separate from a stated intake time. */
        suspend fun applyLocal(
            key: OccurrenceKey,
            actedAt: Instant,
            block: suspend () -> Unit,
        ): Unit =
            db.withTransaction {
                block()
                val receipts = db.watchActionReceiptDao()
                val encoded = key.encode()
                val latest = receipts.latestActionAt(encoded) ?: Long.MIN_VALUE
                receipts.upsertCursor(DoseActionCursorEntity(encoded, maxOf(latest, actedAt.toEpochMilli())))
            }
    }
