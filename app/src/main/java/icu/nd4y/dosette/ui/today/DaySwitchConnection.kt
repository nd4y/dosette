package icu.nd4y.dosette.ui.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Telegram-style day switching: keep dragging past the end of the list
 * to open the next day, past the top to open the previous one. Watches
 * the scroll deltas the list could not consume; a switch fires once per
 * gesture after [thresholdPx] of overscroll in one direction.
 */
class DaySwitchConnection(
    private val thresholdPx: Float,
    private val onPreviousDay: () -> Unit,
    private val onNextDay: () -> Unit,
) : NestedScrollConnection {
    private var overscroll = 0f
    private var fired = false

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // The list is consuming again — the finger came back inside.
        if (available.y != 0f && !fired) resetIfDirectionChanged(available.y)
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput && !fired) {
            if (consumed.y != 0f) {
                // Still really scrolling; only pure overscroll counts.
                overscroll = 0f
            } else {
                overscroll += available.y
                when {
                    overscroll <= -thresholdPx -> {
                        fired = true
                        onNextDay()
                    }

                    overscroll >= thresholdPx -> {
                        fired = true
                        onPreviousDay()
                    }
                }
            }
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Gesture is ending — arm for the next one.
        overscroll = 0f
        fired = false
        return Velocity.Zero
    }

    private fun resetIfDirectionChanged(direction: Float) {
        if (overscroll != 0f && (overscroll > 0) != (direction > 0)) overscroll = 0f
    }
}
