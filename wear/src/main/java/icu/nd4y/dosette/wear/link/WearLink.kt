package icu.nd4y.dosette.wear.link

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import icu.nd4y.dosette.link.LinkPaths
import icu.nd4y.dosette.link.WatchAction
import icu.nd4y.dosette.link.WatchJson
import icu.nd4y.dosette.link.WatchSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** The watch's end of the Wearable Data Layer; faked in tests, Play services in the app. */
interface WearLink {
    /** The phone's latest picture of the day, null until a phone has ever published one. Re-emits on every change. */
    val snapshot: Flow<WatchSnapshot?>

    /** Taps queued on this watch that the phone has not acknowledged yet. */
    val pendingActions: Flow<List<WatchAction>>

    /** Queues a tap for the phone; false only when Play services refused it outright. */
    suspend fun send(action: WatchAction): Boolean

    /** Whether a phone running Dosette is reachable right now. */
    suspend fun phoneReachable(): Boolean
}

/**
 * Play services implementation. The Data Layer keeps the last item of each
 * path on the watch itself, so the day is readable with the phone out of
 * reach, and a queued tap is delivered once it is back.
 */
@Singleton
class GmsWearLink
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WearLink {
        private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
        private val capabilityClient: CapabilityClient by lazy { Wearable.getCapabilityClient(context) }

        override val snapshot: Flow<WatchSnapshot?> =
            items(Uri.parse(ANY_NODE + LinkPaths.TODAY), DataClient.FILTER_LITERAL)
                .map { payloads -> payloads.firstNotNullOfOrNull(WatchJson::decodeSnapshot) }

        override val pendingActions: Flow<List<WatchAction>> =
            items(Uri.parse(ANY_NODE + LinkPaths.ACTIONS), DataClient.FILTER_PREFIX)
                .map { payloads -> payloads.mapNotNull(WatchJson::decodeAction) }

        override suspend fun send(action: WatchAction): Boolean {
            val request =
                PutDataMapRequest
                    .create(LinkPaths.actionPath(action.id))
                    .apply { dataMap.putString(LinkPaths.KEY_JSON, WatchJson.encode(action)) }
                    .asPutDataRequest()
                    .setUrgent()
            return runCatching { dataClient.putDataItem(request).await() }
                .onFailure { Log.w(TAG, "send failed", it) }
                .isSuccess
        }

        override suspend fun phoneReachable(): Boolean =
            runCatching {
                capabilityClient
                    .getCapability(LinkPaths.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                    .isNotEmpty()
            }.onFailure { Log.w(TAG, "capability query failed", it) }
                .getOrDefault(false)

        /** JSON payloads of the items under [uri], re-read after every change while collected. */
        private fun items(
            uri: Uri,
            filterType: Int,
        ): Flow<List<String>> =
            callbackFlow {
                suspend fun refresh() {
                    trySend(readAll(uri, filterType))
                }
                val listener = DataClient.OnDataChangedListener { launch { refresh() } }
                runCatching { dataClient.addListener(listener, uri, filterType).await() }
                    .onFailure { Log.w(TAG, "listener registration failed", it) }
                refresh()
                awaitClose { dataClient.removeListener(listener) }
            }

        private suspend fun readAll(
            uri: Uri,
            filterType: Int,
        ): List<String> =
            runCatching {
                val buffer = dataClient.getDataItems(uri, filterType).await()
                try {
                    buffer.map { item ->
                        DataMapItem
                            .fromDataItem(item)
                            .dataMap
                            .getString(LinkPaths.KEY_JSON)
                            .orEmpty()
                    }
                } finally {
                    buffer.release()
                }
            }.onFailure { Log.w(TAG, "read failed", it) }
                .getOrDefault(emptyList())

        private companion object {
            const val TAG = "WearLink"
            const val ANY_NODE = "wear://*"
        }
    }
