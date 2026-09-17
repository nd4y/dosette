package icu.nd4y.dosette.watch

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import icu.nd4y.dosette.link.LinkPaths
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** An action item a watch put on the Data Layer, still to be applied. */
data class PendingWatchAction(
    /** Full item URI (`wear://<node>/dosette/action/<id>`); deleting it acknowledges the action to the watch. */
    val uri: String,
    val json: String,
)

/** The phone's end of the Wearable Data Layer; faked in tests, Play services in the app. */
interface WatchLink {
    /** Replaces the day picture every paired watch reads. Identical content is not re-sent. */
    suspend fun publishSnapshot(json: String)

    /** Action items no watch has been acknowledged for yet. */
    suspend fun pendingActions(): List<PendingWatchAction>

    /** Deletes the item; the watch sees the deletion and stops showing the action as in flight. */
    suspend fun acknowledge(uri: String)
}

/**
 * Play services implementation. Nothing here may throw into the reminder
 * engine: a phone without Play services or without a paired watch simply
 * has no link, and every call degrades to a log line.
 */
@Singleton
class GmsWatchLink
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WatchLink {
        private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }

        override suspend fun publishSnapshot(json: String) {
            val request =
                PutDataMapRequest
                    .create(LinkPaths.TODAY)
                    .apply { dataMap.putString(LinkPaths.KEY_JSON, json) }
                    .asPutDataRequest()
                    // The watch is waiting for exactly this after a tap; no batching delay.
                    .setUrgent()
            guarded("publish") { dataClient.putDataItem(request).await() }
        }

        override suspend fun pendingActions(): List<PendingWatchAction> =
            guarded("list") {
                val buffer =
                    dataClient
                        .getDataItems(Uri.parse("wear://*" + LinkPaths.ACTIONS), DataClient.FILTER_PREFIX)
                        .await()
                try {
                    buffer.map { item ->
                        PendingWatchAction(
                            uri = item.uri.toString(),
                            json =
                                DataMapItem
                                    .fromDataItem(item)
                                    .dataMap
                                    .getString(LinkPaths.KEY_JSON)
                                    .orEmpty(),
                        )
                    }
                } finally {
                    buffer.release()
                }
            } ?: emptyList()

        override suspend fun acknowledge(uri: String) {
            guarded("acknowledge") { dataClient.deleteDataItems(Uri.parse(uri)).await() }
        }

        private suspend fun <T> guarded(
            what: String,
            block: suspend () -> T,
        ): T? =
            runCatching { block() }
                .onFailure { Log.w(TAG, "$what: Wearable API call failed", it) }
                .getOrNull()

        private companion object {
            const val TAG = "WatchLink"
        }
    }
