package icu.nd4y.dosette.watch.install

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WatchInstallProtocolTest {
    @get:Rule val folder = TemporaryFolder()
    private val candidateIdentity = WearApkIdentity(WATCH_PACKAGE, 14001, "0.14.0", "trusted")
    private val previousIdentity = candidateIdentity.copy(versionCode = 1301, versionName = "0.13.1")

    @Test fun `first install verifies resulting APK without clearing data`() {
        val device = Device(null)
        val result = protocol(device).install(device, candidate(), scratch()) {}
        assertThat(result.installed).isEqualTo(candidateIdentity)
        assertThat(device.commands).contains("cmd package install-create -r --user current -S 3")
        assertThat(device.commands).contains("cmd package install-commit 42")
        assertThat(device.commands.any { "uninstall" in it || "clear" in it || " -d" in it }).isFalse()
    }

    @Test fun `update preserves existing sentinel and pending queue`() {
        val device = Device(previousIdentity)
        protocol(device).install(device, candidate(), scratch()) {}
        assertThat(device.userData).containsExactly("pending-actions", "preserved", "preferences", "preserved")
        assertThat(device.writes).isEqualTo(1)
    }

    @Test fun `installed signature mismatch prevents installer session`() {
        val device = Device(previousIdentity.copy(certificate = "foreign"))
        expect(InstallFailure.SIGNATURE) { protocol(device).install(device, candidate(), scratch()) {} }
        assertNoSession(device)
    }

    @Test fun `candidate must match phone signing identity`() {
        val device = Device(null)
        expect(InstallFailure.SIGNATURE) {
            protocol(
                device,
            ).install(device, candidate().copy(identity = candidateIdentity.copy(certificate = "foreign")), scratch()) {
            }
        }
        assertNoSession(device)
    }

    @Test fun `newer installed version is never downgraded`() {
        val device = Device(candidateIdentity.copy(versionCode = 99999))
        expect(InstallFailure.DOWNGRADE) { protocol(device).install(device, candidate(), scratch()) {} }
        assertNoSession(device)
    }

    @Test fun `same version does not reinstall`() {
        val device = Device(candidateIdentity)
        assertThat(protocol(device).install(device, candidate(), scratch()) {}.upToDate).isTrue()
        assertNoSession(device)
    }

    @Test fun `rejects phone instead of watch`() {
        val device = Device(null, watch = false)
        expect(InstallFailure.NOT_WATCH) { protocol(device).install(device, candidate(), scratch()) {} }
        assertNoSession(device)
    }

    @Test fun `candidate phone APK cannot be installed on watch`() {
        val device = Device(null)
        expect(InstallFailure.PACKAGE) {
            protocol(
                device,
            ).install(device, candidate().copy(identity = candidateIdentity.copy(watch = false)), scratch()) {}
        }
        assertNoSession(device)
    }

    @Test fun `tampered installed APK blocks update and scratch is removed`() {
        val device = Device(previousIdentity)
        val scratch = scratch()
        val protocol = WatchInstallProtocol({ throw WatchInstallException(InstallFailure.SIGNATURE) }, "trusted")
        expect(InstallFailure.SIGNATURE) { protocol.install(device, candidate(), scratch) {} }
        assertThat(scratch.exists()).isFalse()
        assertNoSession(device)
    }

    @Test fun `rechecks installed version when update starts`() {
        val device = Device(previousIdentity)
        val protocol = protocol(device)
        protocol.inspect(device, candidate(), scratch())
        device.installed = candidateIdentity.copy(versionCode = 99999)
        expect(InstallFailure.DOWNGRADE) { protocol.install(device, candidate(), scratch()) {} }
        assertNoSession(device)
    }

    @Test fun `failed transfer abandons session without committing or clearing data`() {
        val device = Device(previousIdentity, failWrite = true)
        expect(InstallFailure.CONNECTION) { protocol(device).install(device, candidate(), scratch()) {} }
        assertThat(device.commands).contains("cmd package install-abandon 42")
        assertThat(device.commands).doesNotContain("cmd package install-commit 42")
        assertThat(device.installed).isEqualTo(previousIdentity)
    }

    @Test fun `failed commit is not reported as success`() {
        val device = Device(previousIdentity, commitResult = "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]")
        expect(InstallFailure.INSTALL) { protocol(device).install(device, candidate(), scratch()) {} }
        assertThat(device.commands).contains("cmd package install-abandon 42")
        assertThat(device.installed).isEqualTo(previousIdentity)
    }

    @Test fun `post install version mismatch is reported as unverified`() {
        val device = Device(previousIdentity, finalIdentity = previousIdentity)
        expect(InstallFailure.VERIFY) { protocol(device).install(device, candidate(), scratch()) {} }
    }

    @Test fun `untrusted pm path cannot inject shell commands`() {
        val device = Device(previousIdentity, path = "/data/app/pkg/base.apk;reboot")
        expect(InstallFailure.PACKAGE) { protocol(device).inspect(device, candidate(), scratch()) }
        assertThat(device.pulls).isEqualTo(0)
    }

    @Test fun `rejects invalid numeric addresses and ports`() {
        listOf("localhost", "127.0.0.1", "0.0.0.0", "224.0.0.1", "1.2.3.256", "1.2.3.4;reboot", "01.2.3.4").forEach {
            expect(InstallFailure.ADDRESS) { AdbEndpoint.parse(it, "12345") }
        }
        listOf("0", "65536", "123;id", "-1", "").forEach {
            expect(InstallFailure.ADDRESS) { AdbEndpoint.parse("192.168.1.20", it) }
        }
        assertThat(AdbEndpoint.parse("192.168.1.20", "43210")).isEqualTo(AdbEndpoint("192.168.1.20", 43210))
    }

    private fun expect(
        reason: InstallFailure,
        action: () -> Unit,
    ) {
        assertThat(assertThrows(WatchInstallException::class.java, action).reason).isEqualTo(reason)
    }

    private fun assertNoSession(device: Device) {
        assertThat(device.commands.any { "install-" in it }).isFalse()
    }

    private fun candidate(): VerifiedWearApk =
        VerifiedWearApk(
            folder.newFile().apply {
                writeBytes(byteArrayOf(1, 2, 3))
            },
            candidateIdentity,
        )

    private fun scratch(): File = folder.newFile()

    private fun protocol(device: Device): WatchInstallProtocol = WatchInstallProtocol({ device.installed!! }, "trusted")

    private inner class Device(
        var installed: WearApkIdentity?,
        val watch: Boolean = true,
        val failWrite: Boolean = false,
        val commitResult: String = "Success",
        val finalIdentity: WearApkIdentity = candidateIdentity,
        val path: String = "/data/app/~~abc/pkg==/base.apk",
    ) : WatchDevice {
        val commands = mutableListOf<String>()
        val userData = mutableMapOf("pending-actions" to "preserved", "preferences" to "preserved")
        var writes = 0
        var pulls = 0

        override fun command(command: String): String {
            commands += command
            return when {
                command == "pm list features" -> {
                    if (watch) "feature:android.hardware.type.watch\n" else ""
                }

                command == "getprop ro.build.version.sdk" -> {
                    "36\n"
                }

                command.startsWith("pm path") -> {
                    if (installed == null) "" else "package:$path\n"
                }

                command.startsWith("cmd package install-create") -> {
                    "Success: created install session [42]\n"
                }

                command == "cmd package install-commit 42" -> {
                    if (commitResult == "Success") installed = finalIdentity
                    commitResult
                }

                else -> {
                    "Success"
                }
            }
        }

        override fun pullApk(
            path: String,
            destination: File,
        ) {
            pulls++
            destination.writeText("signed APK fixture")
        }

        override fun writeApk(
            session: Int,
            apk: File,
            progress: (Int) -> Unit,
        ) {
            if (failWrite) throw WatchInstallException(InstallFailure.CONNECTION)
            writes++
            progress(100)
        }
    }
}
