package icu.nd4y.dosette.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Sizes of a 5x6 Pixel Launcher grid: 65x100 dp cells, 15 / 11 dp gutters. */
class WidgetLayoutTest {
    @Test
    fun `two cells wide is the next-dose card whatever the height`() {
        assertThat(widgetLayoutFor(DpSize(146.dp, 211.dp))).isEqualTo(WidgetLayout.COMPACT)
        assertThat(widgetLayoutFor(DpSize(146.dp, 322.dp))).isEqualTo(WidgetLayout.COMPACT)
    }

    @Test
    fun `a wide two-row cell shows the next slot and three rows the day`() {
        assertThat(widgetLayoutFor(DpSize(307.dp, 211.dp))).isEqualTo(WidgetLayout.MEDIUM)
        assertThat(widgetLayoutFor(DpSize(307.dp, 322.dp))).isEqualTo(WidgetLayout.LARGE)
    }
}
