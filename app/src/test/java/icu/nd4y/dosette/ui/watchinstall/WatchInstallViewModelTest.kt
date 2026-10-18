package icu.nd4y.dosette.ui.watchinstall

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.testing.MainDispatcherRule
import icu.nd4y.dosette.testing.clearForTest
import icu.nd4y.dosette.watch.install.AdbEndpoint
import icu.nd4y.dosette.watch.install.InstallFailure
import icu.nd4y.dosette.watch.install.WATCH_PACKAGE
import icu.nd4y.dosette.watch.install.WatchInstallException
import icu.nd4y.dosette.watch.install.WatchInstallPlan
import icu.nd4y.dosette.watch.install.WatchInstaller
import icu.nd4y.dosette.watch.install.WearApkIdentity
import kotlinx.coroutines.awaitCancellation
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
}
