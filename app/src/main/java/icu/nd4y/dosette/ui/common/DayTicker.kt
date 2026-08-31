package icu.nd4y.dosette.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * Emits today's date and re-emits right after midnight, so screens that
 * capture "today" roll over without waiting for a database change.
 */
fun dayTicker(clock: Clock): Flow<LocalDate> =
    flow {
        while (true) {
            val date = clock.instant().atZone(clock.zone).toLocalDate()
            emit(date)
            val nextMidnight =
                date
                    .plusDays(1)
                    .atStartOfDay(clock.zone)
                    .toInstant()
            delay(Duration.between(clock.instant(), nextMidnight).toMillis().coerceAtLeast(1_000))
        }
    }
