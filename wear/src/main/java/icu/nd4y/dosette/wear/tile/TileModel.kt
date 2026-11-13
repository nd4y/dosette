package icu.nd4y.dosette.wear.tile

import icu.nd4y.dosette.link.WatchDose
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.link.WatchSnapshot
import java.time.LocalDate

data class TileModel(
    val hasData: Boolean,
    val stale: Boolean,
    val remaining: Int,
    val next: WatchDose?,
)

fun tileModel(
    snapshot: WatchSnapshot?,
    today: LocalDate,
): TileModel {
    val pending =
        snapshot
            ?.doses
            .orEmpty()
            .filter {
                it.status == WatchDoseStatus.PENDING ||
                    it.status == WatchDoseStatus.MISSED
            }.sortedWith(compareBy({ !it.carryover }, { it.timeMinutes }))
    return TileModel(
        snapshot != null,
        snapshot != null && snapshot.date != today.toString(),
        pending.size,
        pending.firstOrNull(),
    )
}
