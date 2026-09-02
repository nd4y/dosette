package icu.nd4y.dosette.testing

import icu.nd4y.dosette.reminders.WidgetRefresher

/** Counts refresh requests; every mutating engine pass ends with one. */
class FakeWidgetRefresher : WidgetRefresher {
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
    }
}
