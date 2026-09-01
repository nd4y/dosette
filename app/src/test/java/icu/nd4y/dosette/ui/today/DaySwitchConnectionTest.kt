package icu.nd4y.dosette.ui.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DaySwitchConnectionTest {
    private var previous = 0
    private var next = 0
    private val connection =
        DaySwitchConnection(
            thresholdPx = 100f,
            onPreviousDay = { previous++ },
            onNextDay = { next++ },
        )

    private fun overscroll(y: Float) {
        connection.onPostScroll(Offset.Zero, Offset(0f, y), NestedScrollSource.UserInput)
    }

    @Test
    fun `dragging up past the end beyond the threshold opens the next day once`() {
        repeat(5) { overscroll(-30f) }

        assertThat(next).isEqualTo(1)
        assertThat(previous).isEqualTo(0)
    }

    @Test
    fun `dragging down at the top beyond the threshold opens the previous day`() {
        repeat(4) { overscroll(40f) }

        assertThat(previous).isEqualTo(1)
        assertThat(next).isEqualTo(0)
    }

    @Test
    fun `real scrolling in between resets the overscroll`() {
        overscroll(-60f)
        // The list consumed something: the finger is back inside the content.
        connection.onPostScroll(Offset(0f, -20f), Offset(0f, -60f), NestedScrollSource.UserInput)
        overscroll(-60f)

        assertThat(next).isEqualTo(0)
    }

    @Test
    fun `a fling ending the gesture re-arms the switch`() =
        runTest {
            repeat(5) { overscroll(-30f) }
            assertThat(next).isEqualTo(1)
            // Still the same gesture: no double fire.
            overscroll(-200f)
            assertThat(next).isEqualTo(1)

            connection.onPreFling(Velocity.Zero)
            repeat(5) { overscroll(-30f) }
            assertThat(next).isEqualTo(2)
        }

    @Test
    fun `small overscroll below the threshold does nothing`() {
        overscroll(-99f)

        assertThat(next).isEqualTo(0)
        assertThat(previous).isEqualTo(0)
    }
}
