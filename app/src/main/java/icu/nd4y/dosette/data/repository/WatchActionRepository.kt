package icu.nd4y.dosette.data.repository

import androidx.room.withTransaction
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.entity.WatchActionReceiptEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Durable inbox: database mutations and their receipt commit or roll back together. */
@Singleton
class WatchActionRepository
    @Inject
    constructor(
        private val db: AppDatabase,
    ) {
        suspend fun applyOnce(
            deliveryId: String,
            block: suspend () -> Unit,
        ): Unit =
            db.withTransaction {
                val receipts = db.watchActionReceiptDao()
                if (!receipts.contains(deliveryId)) {
                    block()
                    receipts.insert(WatchActionReceiptEntity(deliveryId))
                }
            }
    }
