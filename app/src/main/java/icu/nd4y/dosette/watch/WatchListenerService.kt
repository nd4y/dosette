package icu.nd4y.dosette.watch

import android.util.Log
import androidx.core.os.UserManagerCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.di.IoDispatcher
import icu.nd4y.dosette.link.LinkPaths
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Woken by Play services when a watch puts an action item, whether or not
 * the app is running. The work is done before returning: the service is
 * stopped right after the callback, so a coroutine outliving it could be
 * killed mid-write.
 */
@AndroidEntryPoint
class WatchListenerService : WearableListenerService() {
    @Inject
    lateinit var inbox: WatchInbox

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onDataChanged(events: DataEventBuffer) {
        // Copied first: the buffer is recycled once this method returns.
        val actions =
            events
                .filter { event ->
                    event.type == DataEvent.TYPE_CHANGED &&
                        event.dataItem.uri.path
                            .orEmpty()
                            .startsWith(LinkPaths.ACTIONS)
                }.map { event ->
                    PendingWatchAction(
                        uri = event.dataItem.uri.toString(),
                        json =
                            DataMapItem
                                .fromDataItem(event.dataItem)
                                .dataMap
                                .getString(LinkPaths.KEY_JSON)
                                .orEmpty(),
                    )
                }
        if (actions.isEmpty()) return
        // Before the first unlock the database is unreadable; the items stay
        // on the Data Layer and the boot reconcile drains them.
        if (!UserManagerCompat.isUserUnlocked(this)) return
        runBlocking(ioDispatcher) {
            runCatching { inbox.receive(actions) }.onFailure { Log.e("WatchListener", "watch actions failed", it) }
        }
    }
}
