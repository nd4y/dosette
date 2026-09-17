package icu.nd4y.dosette.wear.testing

import icu.nd4y.dosette.link.WatchAction
import icu.nd4y.dosette.link.WatchSnapshot
import icu.nd4y.dosette.wear.link.WearLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A Data Layer in memory: the test sets what the phone published and sees what the watch queued. */
class FakeWearLink : WearLink {
    val snapshots = MutableStateFlow<WatchSnapshot?>(null)
    val pending = MutableStateFlow<List<WatchAction>>(emptyList())

    /** Everything [send] accepted, in order. */
    val sent = mutableListOf<WatchAction>()

    var reachable = true
    var accepting = true

    override val snapshot: Flow<WatchSnapshot?> get() = snapshots
    override val pendingActions: Flow<List<WatchAction>> get() = pending

    override suspend fun send(action: WatchAction): Boolean {
        if (!accepting) return false
        sent += action
        pending.value = pending.value + action
        return true
    }

    override suspend fun phoneReachable(): Boolean = reachable
}
