package icu.nd4y.dosette.watch.install

import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.PairingConnectionCtx
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import icu.nd4y.dosette.watch.install.InstallFailure as Failure

interface WatchInstaller {
    suspend fun pair(
        endpoint: AdbEndpoint,
        code: String,
    )

    suspend fun inspect(endpoint: AdbEndpoint): WatchInstallPlan

    suspend fun install(
        endpoint: AdbEndpoint,
        progress: (Int) -> Unit,
    ): WatchInstallPlan
}

@Singleton
class LocalWatchInstaller
    @Inject
    constructor(
        private val identityStore: AdbIdentityStore,
        private val verifier: WearApkVerifier,
    ) : WatchInstaller {
        private val worker = Executors.newSingleThreadExecutor()
        private val closer = Executors.newCachedThreadPool()

        override suspend fun pair(
            endpoint: AdbEndpoint,
            code: String,
        ) {
            if (!code.matches(Regex("[0-9]{6}"))) throw WatchInstallException(Failure.PAIRING)
            runIo(Failure.PAIRING) { operation ->
                val identity = identityStore.load()
                operation
                    .track(
                        PairingConnectionCtx(
                            endpoint.host,
                            endpoint.port,
                            code.toByteArray(),
                            identity.key,
                            identity.certificate,
                            "Dosette",
                        ),
                    ).use { it.start() }
            }
        }

        override suspend fun inspect(endpoint: AdbEndpoint): WatchInstallPlan =
            runIo(Failure.CONNECTION) { operation ->
                connected(endpoint, operation) { device, candidate, scratch, protocol ->
                    protocol.inspect(device, candidate, scratch)
                }
            }

        override suspend fun install(
            endpoint: AdbEndpoint,
            progress: (Int) -> Unit,
        ): WatchInstallPlan =
            runIo(Failure.INTERRUPTED) { operation ->
                connected(endpoint, operation) { device, candidate, scratch, protocol ->
                    protocol.install(device, candidate, scratch, progress)
                }
            }

        private fun <T> connected(
            endpoint: AdbEndpoint,
            operation: Operation,
            block: (WatchDevice, VerifiedWearApk, File, WatchInstallProtocol) -> T,
        ): T {
            val candidate = verifier.bundled()
            val identity = identityStore.load()
            val connection =
                operation.track(
                    AdbConnection
                        .Builder(endpoint.host, endpoint.port)
                        .setApi(30)
                        .setPrivateKey(identity.key)
                        .setCertificate(identity.certificate)
                        .setDeviceName("Dosette")
                        .build(),
                )
            connection.use {
                if (!connection.connect(15, TimeUnit.SECONDS, true) ||
                    !connection.isTls
                ) {
                    throw WatchInstallException(Failure.CONNECTION)
                }
                val scratch = File(verifier.workDirectory(), "installed.apk")
                return block(
                    AdbWatchDevice(connection),
                    candidate,
                    scratch,
                    WatchInstallProtocol(verifier::verify, verifier.phoneCertificate()),
                )
            }
        }

        // Translate third-party I/O/crypto errors without exposing keys, codes, or device output to UI/logs.
        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T> runIo(
            why: Failure,
            block: (Operation) -> T,
        ): T =
            withTimeout(180_000) {
                suspendCancellableCoroutine { continuation ->
                    val operation = Operation()
                    val future =
                        worker.submit {
                            try {
                                operation.checkActive()
                                val result = block(operation)
                                if (continuation.isActive) continuation.resume(result)
                            } catch (error: Exception) {
                                if (continuation.isActive) {
                                    val problem = error as? WatchInstallException ?: WatchInstallException(why)
                                    continuation.resumeWithException(problem)
                                }
                            } finally {
                                operation.close()
                            }
                        }
                    continuation.invokeOnCancellation {
                        operation.cancelled.set(true)
                        future.cancel(true)
                        closer.execute { operation.close() }
                    }
                }
            }

        private class Operation : Closeable {
            val cancelled = AtomicBoolean()
            private val resources = CopyOnWriteArrayList<Closeable>()

            fun <T : Closeable> track(resource: T): T {
                resources.add(resource)
                if (cancelled.get()) {
                    resource.close()
                    checkActive()
                }
                return resource
            }

            fun checkActive() {
                if (cancelled.get() || Thread.currentThread().isInterrupted) {
                    throw WatchInstallException(Failure.INTERRUPTED)
                }
            }

            override fun close() {
                cancelled.set(true)
                resources.forEach { runCatching { it.close() } }
                resources.clear()
            }
        }
    }
