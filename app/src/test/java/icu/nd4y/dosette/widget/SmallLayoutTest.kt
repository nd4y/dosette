package icu.nd4y.dosette.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmallLayoutTest {
    @Test
    fun `the nominal compact bucket keeps only the ring row and the button`() {
        val plan = SmallLayout.compact(widthDp = 146, heightDp = 110)

        assertThat(plan.showName).isFalse()
        assertThat(plan.showSubtitle).isFalse()
    }

    @Test
    fun `a taller compact cell lists the name and the dose`() {
        assertThat(SmallLayout.compact(widthDp = 146, heightDp = 150)).isEqualTo(SmallLayout.CompactPlan(true, true))
        // Larger fonts eat the subtitle first, then the name.
        assertThat(SmallLayout.compact(widthDp = 146, heightDp = 150, fontScale = 1.5f))
            .isEqualTo(SmallLayout.CompactPlan(showName = true, showSubtitle = false))
    }

    @Test
    fun `the provider minimum and a cramped cell get the tight card`() {
        assertThat(SmallLayout.compact(widthDp = 110, heightDp = 110).tight).isTrue()
        // Too short for the regular card even when wide enough.
        assertThat(SmallLayout.compact(widthDp = 146, heightDp = 100).tight).isTrue()
        assertThat(SmallLayout.compact(widthDp = 146, heightDp = 211).tight).isFalse()
    }

    @Test
    fun `the nominal medium bucket lists one row and counts the rest`() {
        val plan = SmallLayout.medium(heightDp = 110, doseCount = 3)

        assertThat(plan).isEqualTo(SmallLayout.MediumPlan(rows = 1, hidden = 2))
    }

    @Test
    fun `a two-row launcher cell lists three rows of a full slot`() {
        // Pixel Launcher: two 100dp rows plus the gutter, exact size mode.
        assertThat(SmallLayout.medium(heightDp = 211, doseCount = 4))
            .isEqualTo(SmallLayout.MediumPlan(rows = 3, hidden = 1))
    }

    @Test
    fun `a taller medium cell lists two rows`() {
        assertThat(SmallLayout.medium(heightDp = 150, doseCount = 3)).isEqualTo(SmallLayout.MediumPlan(2, 1))
        assertThat(SmallLayout.medium(heightDp = 150, doseCount = 1)).isEqualTo(SmallLayout.MediumPlan(1, 0))
        // At a large font the "+N more" line no longer fits under two rows.
        assertThat(SmallLayout.medium(heightDp = 150, doseCount = 3, fontScale = 1.3f))
            .isEqualTo(SmallLayout.MediumPlan(rows = 1, hidden = 2))
    }
}
