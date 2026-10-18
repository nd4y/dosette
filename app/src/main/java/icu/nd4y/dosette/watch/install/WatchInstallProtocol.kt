package icu.nd4y.dosette.watch.install

import java.io.File

/** Narrow protocol boundary: callers cannot choose a package, arbitrary command or APK. */
interface WatchDevice {
    fun command(command: String): String

    fun pullApk(
        path: String,
        destination: File,
    )

    fun writeApk(
        session: Int,
        apk: File,
        progress: (Int) -> Unit,
    )
}

class WatchInstallProtocol(
    private val verify: (File) -> WearApkIdentity,
    private val phoneCertificate: String,
) {
    fun inspect(
        device: WatchDevice,
        candidate: VerifiedWearApk,
        scratch: File,
    ): WatchInstallPlan {
        if ("feature:android.hardware.type.watch" !in device.command("pm list features").lineSequence()) {
            throw WatchInstallException(InstallFailure.NOT_WATCH)
        }
        val sdk = device.command("getprop ro.build.version.sdk").trim().toIntOrNull()
        if (sdk == null || sdk < 30) throw WatchInstallException(InstallFailure.NOT_WATCH)
        val installed = readInstalled(device, scratch)
        validateWearUpdate(candidate.identity, installed, phoneCertificate)
        return WatchInstallPlan(candidate.identity, installed)
    }

    @Suppress("ThrowsCount") // Each install stage must fail closed before proceeding to the next.
    fun install(
        device: WatchDevice,
        candidate: VerifiedWearApk,
        scratch: File,
        progress: (Int) -> Unit,
    ): WatchInstallPlan {
        val plan = inspect(device, candidate, scratch)
        if (plan.upToDate) return plan
        val created = device.command("cmd package install-create -r --user current -S ${candidate.file.length()}")
        val session =
            Regex("Success: created install session \\[([0-9]{1,10})]")
                .find(created)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: throw WatchInstallException(InstallFailure.STORAGE)
        var committed = false
        try {
            device.writeApk(session, candidate.file, progress)
            val result = device.command("cmd package install-commit $session").trim()
            if (result != "Success") throw WatchInstallException(InstallFailure.INSTALL)
            committed = true
            val installed = readInstalled(device, scratch)
            if (installed != candidate.identity) throw WatchInstallException(InstallFailure.VERIFY)
            return WatchInstallPlan(candidate.identity, installed)
        } finally {
            if (!committed) runCatching { device.command("cmd package install-abandon $session") }
        }
    }

    private fun readInstalled(
        device: WatchDevice,
        scratch: File,
    ): WearApkIdentity? {
        val output = device.command("pm path $WATCH_PACKAGE").trim()
        if (output.isEmpty()) return null
        val paths = output.lines().map { it.trim().removePrefix("package:") }
        val path =
            paths.singleOrNull { it.endsWith("/base.apk") }
                ?: throw WatchInstallException(InstallFailure.PACKAGE)
        if (!path.matches(Regex("/data/app/[A-Za-z0-9_./=+~\\-]+/base\\.apk"))) {
            throw WatchInstallException(InstallFailure.PACKAGE)
        }
        try {
            device.pullApk(path, scratch)
            return verify(scratch)
        } finally {
            scratch.delete()
        }
    }
}
