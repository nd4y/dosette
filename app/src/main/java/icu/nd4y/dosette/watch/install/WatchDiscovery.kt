package icu.nd4y.dosette.watch.install

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.net.Inet4Address
import javax.inject.Inject
import kotlin.coroutines.resume

enum class AdbServiceKind(
    val type: String,
) {
    PAIRING("_adb-tls-pairing._tcp."),
    CONNECTION("_adb-tls-connect._tcp."),
}

data class DiscoveredAdbService(
    val name: String,
    val kind: AdbServiceKind,
    val endpoint: AdbEndpoint,
)

data class WatchDiscoveryState(
    val services: List<DiscoveredAdbService> = emptyList(),
    val unavailable: Boolean = false,
)

interface WatchDiscovery {
    fun observe(): Flow<WatchDiscoveryState>
}

object NoWatchDiscovery : WatchDiscovery {
    override fun observe(): Flow<WatchDiscoveryState> = flowOf(WatchDiscoveryState(unavailable = true))
}

/** Advertisements are hints. Installation still verifies watch type, package and signer. */
class AndroidWatchDiscovery
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : WatchDiscovery {
        private val nsd = context.getSystemService(NsdManager::class.java)

        override fun observe(): Flow<WatchDiscoveryState> =
            callbackFlow {
                val session = DiscoverySession(nsd, this)
                session.start()
                awaitClose { session.close() }
            }
    }

private class DiscoverySession(
    private val nsd: NsdManager,
    private val scope: ProducerScope<WatchDiscoveryState>,
) : Closeable {
    private val lock = Any()
    private val found = mutableSetOf<Pair<AdbServiceKind, String>>()
    private val resolved = mutableMapOf<Pair<AdbServiceKind, String>, DiscoveredAdbService>()
    private val queue = Channel<Pair<AdbServiceKind, NsdServiceInfo>>(64)
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var unavailable = false
    private var resolver: Job? = null

    fun start() {
        resolver =
            scope.launch {
                // Older Android releases permit only one resolution at a time.
                for ((kind, info) in queue) {
                    val service = withTimeoutOrNull(8_000) { resolve(info) }
                    if (service != null) resolved(kind, info.serviceName, service)
                }
            }
        AdbServiceKind.entries.forEach { kind ->
            val listener = listener(kind)
            listeners += listener
            runCatching { nsd.discoverServices(kind.type, NsdManager.PROTOCOL_DNS_SD, listener) }
                .onFailure { failed() }
        }
        emitState()
    }

    private fun emitState() =
        synchronized(lock) {
            val services = resolved.values.sortedWith(compareBy({ it.endpoint.host }, { it.name }))
            scope.trySend(WatchDiscoveryState(services, unavailable))
            Unit
        }

    private fun failed() =
        synchronized(lock) {
            unavailable = true
            emitState()
        }

    @Suppress("DEPRECATION")
    private fun resolved(
        kind: AdbServiceKind,
        name: String,
        info: NsdServiceInfo,
    ) = synchronized(lock) {
        val endpoint = nsdEndpoint(info)
        if ((kind to name) in found) {
            if (endpoint != null) resolved[kind to name] = DiscoveredAdbService(name, kind, endpoint)
            emitState()
        }
    }

    private fun listener(kind: AdbServiceKind) =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit

            override fun onDiscoveryStopped(type: String) = Unit

            override fun onStopDiscoveryFailed(
                type: String,
                code: Int,
            ) = Unit

            override fun onStartDiscoveryFailed(
                type: String,
                code: Int,
            ) = failed()

            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(lock) { found += kind to info.serviceName }
                queue.trySend(kind to info)
            }

            override fun onServiceLost(info: NsdServiceInfo) =
                synchronized(lock) {
                    found -= kind to info.serviceName
                    resolved.remove(kind to info.serviceName)
                    emitState()
                }
        }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            val listener =
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(
                        service: NsdServiceInfo,
                        code: Int,
                    ) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceResolved(service: NsdServiceInfo) {
                        if (continuation.isActive) continuation.resume(service)
                    }
                }
            runCatching { nsd.resolveService(info, listener) }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                if (Build.VERSION.SDK_INT >= 34) runCatching { nsd.stopServiceResolution(listener) }
            }
        }

    override fun close() {
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        queue.close()
        resolver?.cancel()
    }
}

@Suppress("DEPRECATION")
internal fun nsdEndpoint(info: NsdServiceInfo): AdbEndpoint? {
    val addresses = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
    return addresses.filterIsInstance<Inet4Address>().firstNotNullOfOrNull { address ->
        runCatching { AdbEndpoint.parse(requireNotNull(address.hostAddress), info.port.toString()) }.getOrNull()
    }
}
