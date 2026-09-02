package icu.nd4y.dosette.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmallLayoutTest {
    @Test
    fun `the nominal compact bucket keeps only the ring row and the button`() {
        val plan = SmallLayout.compact(heightDp = 110)

        assertThat(plan.showName).isFalse()
        assertThat(plan.showSubtitle).isFalse()
    }

    @Test
    fun `a taller compact cell lists the name and the dose`() {
        assertThat(SmallLayout.compact(heightDp = 150)).isEqualTo(SmallLayout.CompactPlan(true, true))
        // Larger fonts eat the subtitle first, then the name.
        assertThat(SmallLayout.compact(heightDp = 150, fontScale = 1.5f))
            .isEqualTo(SmallLayout.CompactPlan(showName = true, showSubtitle = false))
    }

    @Test
    fun `the nominal medium bucket lists one row and counts the rest`() {
        val plan = SmallLayout.medium(heightDp = 110, doseCount = 3)

        assertThat(plan).isEqualTo(SmallLayout.MediumPlan(rows = 1, hidden = 2))
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
