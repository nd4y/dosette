package icu.nd4y.dosette.ui.watchinstall

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.testing.MainDispatcherRule
import icu.nd4y.dosette.testing.clearForTest
import icu.nd4y.dosette.watch.install.AdbEndpoint
import icu.nd4y.dosette.watch.install.AdbServiceKind
import icu.nd4y.dosette.watch.install.DiscoveredAdbService
import icu.nd4y.dosette.watch.install.InstallFailure
import icu.nd4y.dosette.watch.install.WATCH_PACKAGE
import icu.nd4y.dosette.watch.install.WatchDiscovery
import icu.nd4y.dosette.watch.install.WatchDiscoveryState
import icu.nd4y.dosette.watch.install.WatchInstallException
import icu.nd4y.dosette.watch.install.WatchInstallPlan
import icu.nd4y.dosette.watch.install.WatchInstaller
import icu.nd4y.dosette.watch.install.WearApkIdentity
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WatchInstallViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `pairing and connection use distinct ports then install needs confirmation`() =
        runTest {
            val fake = Installer()
            val vm = WatchInstallViewModel(fake)
            vm.address("192.168.1.20", "12345", "23456")
            vm.pair("123456")
            runCurrent()
            assertThat(fake.pairPort).isEqualTo(12345)
            assertThat(vm.state.value.step).isEqualTo(WatchInstallStep.CONNECT)
            vm.inspect()
            runCurrent()
            assertThat(fake.connectionPort).isEqualTo(23456)
            assertThat(vm.state.value.step).isEqualTo(WatchInstallStep.REVIEW)
            assertThat(fake.installs).isEqualTo(0)
            vm.install()
            runCurrent()
            assertThat(fake.installs).isEqualTo(1)
            assertThat(vm.state.value.step).isEqualTo(WatchInstallStep.DONE)
            vm.clearForTest()
        }

    @Test fun `signature error stays visible and never installs`() =
        runTest {
            val fake = Installer(fail = true)
            val vm = WatchInstallViewModel(fake)
            vm.step(WatchInstallStep.CONNECT)
            vm.address("192.168.1.20", "12345", "23456")
            vm.inspect()
            runCurrent()
            assertThat(vm.state.value.error).isEqualTo(InstallFailure.SIGNATURE)
            assertThat(vm.state.value.busy).isFalse()
            assertThat(fake.installs).isEqualTo(0)
            vm.clearForTest()
        }

    @Test fun `cancelled operation returns to version check without reporting success`() =
        runTest {
            val fake = Installer(wait = true)
            val vm = WatchInstallViewModel(fake)
            vm.address("192.168.1.20", "12345", "23456")
            vm.inspect()
            runCurrent()
            assertThat(vm.state.value.busy).isTrue()
            vm.cancel()
            runCurrent()
            assertThat(vm.state.value.step).isEqualTo(WatchInstallStep.CONNECT)
            assertThat(vm.state.value.error).isEqualTo(InstallFailure.INTERRUPTED)
            assertThat(vm.state.value.busy).isFalse()
            vm.clearForTest()
        }

    private class Installer(
        val fail: Boolean = false,
        val wait: Boolean = false,
    ) : WatchInstaller {
        var pairPort = 0
        var connectionPort = 0
        var installs = 0
        val plan = WatchInstallPlan(WearApkIdentity(WATCH_PACKAGE, 14001, "0.14.0", "certificate"), null)

        override suspend fun pair(
            endpoint: AdbEndpoint,
            code: String,
        ) {
            pairPort = endpoint.port
        }

        override suspend fun inspect(endpoint: AdbEndpoint): WatchInstallPlan {
            if (wait) awaitCancellation()
            if (fail) throw WatchInstallException(InstallFailure.SIGNATURE)
            connectionPort = endpoint.port
            return plan
        }

        override suspend fun install(
            endpoint: AdbEndpoint,
            progress: (Int) -> Unit,
        ): WatchInstallPlan {
            installs++
            return plan.copy(installed = plan.candidate)
        }
    }

    @Test fun `discovery fills pairing endpoint then matches connection on same device`() =
        runTest {
            val advertisements = MutableStateFlow(WatchDiscoveryState())
            val discovery =
                object : WatchDiscovery {
                    override fun observe() = advertisements
                }
            val vm = WatchInstallViewModel(Installer(), discovery)
            vm.visible(true)
            vm.step(WatchInstallStep.PAIR)
            runCurrent()
            advertisements.value =
                WatchDiscoveryState(
                    listOf(
                        DiscoveredAdbService("watch", AdbServiceKind.PAIRING, AdbEndpoint("192.168.1.20", 12345)),
                        DiscoveredAdbService("watch", AdbServiceKind.CONNECTION, AdbEndpoint("192.168.1.20", 23456)),
                        DiscoveredAdbService("other", AdbServiceKind.CONNECTION, AdbEndpoint("192.168.1.30", 34567)),
                    ),
                )
            runCurrent()
            assertThat(vm.state.value.host).isEqualTo("192.168.1.20")
            assertThat(vm.state.value.pairingPort).isEqualTo("12345")
            vm.pair("123456")
            runCurrent()
            assertThat(vm.state.value.connectionPort).isEqualTo("23456")
            assertThat(vm.state.value.host).isEqualTo("192.168.1.20")
            vm.clearForTest()
        }

    @Test
    fun `multiple pairing advertisements require explicit choice`() =
        runTest {
            val services =
                listOf(
                    DiscoveredAdbService("a", AdbServiceKind.PAIRING, AdbEndpoint("192.168.1.20", 12345)),
                    DiscoveredAdbService("b", AdbServiceKind.PAIRING, AdbEndpoint("192.168.1.21", 12346)),
                )
            val discovery =
                object : WatchDiscovery {
                    override fun observe() = flowOf(WatchDiscoveryState(services))
                }
            val vm = WatchInstallViewModel(Installer(), discovery)
            vm.visible(true)
            vm.step(WatchInstallStep.PAIR)
            runCurrent()
            assertThat(vm.state.value.host).isEmpty()
            vm.clearForTest()
        }

    @Test fun `one scan survives step transition and closes when wizard is hidden`() =
        runTest {
            var starts = 0
            var stops = 0
            val discovery =
                object : WatchDiscovery {
                    override fun observe(): Flow<WatchDiscoveryState> =
                        callbackFlow {
                            starts++
                            trySend(WatchDiscoveryState())
                            awaitClose { stops++ }
                        }
                }
            val vm = WatchInstallViewModel(Installer(), discovery)
            vm.visible(true)
            vm.step(WatchInstallStep.PAIR)
            runCurrent()
            vm.step(WatchInstallStep.CONNECT)
            runCurrent()
            assertThat(starts).isEqualTo(1)
            vm.visible(false)
            runCurrent()
            assertThat(stops).isEqualTo(1)
            vm.clearForTest()
        }

    @Test fun `lost automatic connection port is cleared but manual address survives`() {
        val service = DiscoveredAdbService("watch", AdbServiceKind.CONNECTION, AdbEndpoint("192.168.1.20", 23456))
        val selected =
            WatchWizardState(
                step = WatchInstallStep.CONNECT,
                host = service.endpoint.host,
                connectionPort = "23456",
                selectedService = service,
            )
        val lost = selected.withDiscovery(WatchDiscoveryState())
        assertThat(lost.connectionPort).isEmpty()
        assertThat(lost.host).isEqualTo(service.endpoint.host)
        val manual = selected.copy(selectedService = null).withDiscovery(WatchDiscoveryState())
        assertThat(manual.connectionPort).isEqualTo("23456")
    }
}
