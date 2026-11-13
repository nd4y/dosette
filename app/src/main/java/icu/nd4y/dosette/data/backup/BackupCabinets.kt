package icu.nd4y.dosette.data.backup

import icu.nd4y.dosette.domain.backup.CabinetBackup
import icu.nd4y.dosette.domain.backup.CabinetStockBackup
import icu.nd4y.dosette.domain.backup.ProfileBackup
import icu.nd4y.dosette.domain.model.Cabinet
import icu.nd4y.dosette.domain.model.CabinetStock

internal fun Cabinet.toBackup() = CabinetBackup(id, name, sortOrder, createdAt.toString(), archivedAt?.toString())

internal fun CabinetStock.toBackup() = CabinetStockBackup(variantId, cabinetId, currentStock, lastRefillAt?.toString())

internal fun ProfileBackup.cabinetRows(): List<Cabinet> =
    cabinets
        .map {
            Cabinet(
                it.id,
                id,
                it.name,
                it.sortOrder,
                parseInstant(it.createdAt, "cabinet.created_at"),
                it.archivedAt?.let { value -> parseInstant(value, "cabinet.archived_at") },
            )
        }.ifEmpty {
            listOf(Cabinet("main:$id", id, null, 0, parseInstant(createdAt, "profile.created_at")))
        }

internal fun ProfileBackup.stockRows(): List<CabinetStock> =
    if (cabinets.isEmpty()) {
        medications.flatMap { med ->
            med.variants.map {
                CabinetStock(
                    it.id,
                    "main:$id",
                    it.currentStock,
                    it.lastRefillAt?.let { value -> parseInstant(value, "variant.last_refill_at") },
                )
            }
        }
    } else {
        stocks.map {
            CabinetStock(
                it.variantId,
                it.cabinetId,
                it.currentStock,
                it.lastRefillAt?.let { value -> parseInstant(value, "stock.last_refill_at") },
            )
        }
    }
