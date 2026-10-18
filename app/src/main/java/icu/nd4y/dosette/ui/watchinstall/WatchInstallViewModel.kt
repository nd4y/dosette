package icu.nd4y.dosette.ui.watchinstall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.watch.install.AdbEndpoint
import icu.nd4y.dosette.watch.install.InstallFailure
import icu.nd4y.dosette.watch.install.WatchInstallException
import icu.nd4y.dosette.watch.install.WatchInstallPlan
import icu.nd4y.dosette.watch.install.WatchInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WatchInstallStep { PREPARE, PAIR, CONNECT, REVIEW, DONE }

data class WatchWizardState(
    val step: WatchInstallStep = WatchInstallStep.PREPARE,
    val busy: Boolean = false,
    val host: String = "",
    val pairingPort: String = "",
    val connectionPort: String = "",
    val progress: Int = 0,
    val error: InstallFailure? = null,
    val plan: WatchInstallPlan? = null,
)

@HiltViewModel
class WatchInstallViewModel
    @Inject
    constructor(
        private val installer: WatchInstaller,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(WatchWizardState())
        val state = mutableState.asStateFlow()
        private var work: Job? = null

        @Volatile private var generation = 0L

        fun step(step: WatchInstallStep) {
            if (!state.value.busy) mutableState.update { it.copy(step = step, error = null, plan = null) }
        }

        fun address(
            host: String,
            pairingPort: String,
            connectionPort: String,
        ) {
            if (!state.value.busy) {
                mutableState.update {
                    it.copy(
                        host = host.take(15),
                        pairingPort = pairingPort.take(5),
                        connectionPort = connectionPort.take(5),
                        error = null,
                    )
                }
            }
        }

        fun pair(code: String) =
            launchWork {
                val current = state.value
                installer.pair(AdbEndpoint.parse(current.host, current.pairingPort), code)
                mutableState.update { it.copy(step = WatchInstallStep.CONNECT) }
            }

        fun inspect() =
            launchWork {
                val current = state.value
                val plan = installer.inspect(AdbEndpoint.parse(current.host, current.connectionPort))
                mutableState.update { it.copy(step = WatchInstallStep.REVIEW, plan = plan) }
            }

        fun install() =
            launchWork {
                val current = state.value
                if (current.step != WatchInstallStep.REVIEW || current.plan == null) return@launchWork
                val currentGeneration = generation
                val plan =
                    installer.install(AdbEndpoint.parse(current.host, current.connectionPort)) { progress ->
                        if (generation == currentGeneration) mutableState.update { it.copy(progress = progress) }
                    }
                mutableState.update { it.copy(step = WatchInstallStep.DONE, plan = plan) }
            }

        fun cancel() {
            generation++
            work?.cancel()
            mutableState.update {
                it.copy(busy = false, step = WatchInstallStep.CONNECT, plan = null, error = InstallFailure.INTERRUPTED)
            }
        }

        // The installer is a boundary to third-party I/O and crypto providers; only safe error codes reach UI.
        @Suppress("TooGenericExceptionCaught")
        private fun launchWork(block: suspend () -> Unit) {
            if (state.value.busy) return
            val currentGeneration = ++generation
            mutableState.update { it.copy(busy = true, error = null, progress = 0) }
            work =
                viewModelScope.launch {
                    try {
                        block()
                    } catch (_: TimeoutCancellationException) {
                        if (generation == currentGeneration) {
                            mutableState.update {
                                it.copy(
                                    step = WatchInstallStep.CONNECT,
                                    plan = null,
                                    error = InstallFailure.INTERRUPTED,
                                )
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        mutableState.update {
                            it.copy(
                                error =
                                    (failure as? WatchInstallException)?.reason ?: InstallFailure.CONNECTION,
                            )
                        }
                    } finally {
                        if (generation == currentGeneration) mutableState.update { it.copy(busy = false) }
                    }
                }
        }
    }
