package icu.nd4y.dosette.testing

import icu.nd4y.dosette.watch.PendingWatchAction
import icu.nd4y.dosette.watch.WatchLink

/** Records what the phone would put on the Data Layer, and serves a scripted queue. */
class FakeWatchLink : WatchLink {
    /** Every snapshot JSON published, in order. */
    val published = mutableListOf<String>()

    /** What [pendingActions] returns next. */
    val pending = mutableListOf<PendingWatchAction>()

    /** Item URIs acknowledged, in order. */
    val acknowledged = mutableListOf<String>()

    override suspend fun publishSnapshot(json: String) {
        published += json
    }

    override suspend fun pendingActions(): List<PendingWatchAction> = pending.toList()

    override suspend fun acknowledge(uri: String) {
        acknowledged += uri
        pending.removeAll { it.uri == uri }
    }
}
