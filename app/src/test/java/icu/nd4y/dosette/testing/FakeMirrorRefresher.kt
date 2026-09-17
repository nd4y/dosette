package icu.nd4y.dosette.testing

import icu.nd4y.dosette.reminders.MirrorRefresher

/** Counts refresh requests; every mutating engine pass ends with one. */
class FakeMirrorRefresher : MirrorRefresher {
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
    }
}
