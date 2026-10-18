package icu.nd4y.dosette.watch.install

import java.io.File

const val WATCH_PACKAGE = "icu.nd4y.dosette"
const val MAX_WEAR_APK_BYTES = 64L * 1024 * 1024

enum class InstallFailure {
    ADDRESS,
    PAIRING,
    CONNECTION,
    NOT_WATCH,
    SIGNATURE,
    PACKAGE,
    DOWNGRADE,
    STORAGE,
    INSTALL,
    VERIFY,
    INTERRUPTED,
}

class WatchInstallException(
    val reason: InstallFailure,
) : Exception(reason.name)

data class AdbEndpoint(
    val host: String,
    val port: Int,
) {
    companion object {
        // Numeric IPv4 only: no DNS, shell interpolation, URL, or endpoint from an untrusted APK.
        fun parse(
            host: String,
            port: String,
        ): AdbEndpoint {
            val parts = host.trim().split('.')
            val numbers =
                parts.map { part ->
                    part.takeIf { it.matches(Regex("0|[1-9][0-9]{0,2}")) }?.toIntOrNull()
                }
            val validIp =
                parts.size == 4 && numbers.all { it != null && it in 0..255 } &&
                    numbers.first() in 1..223 && numbers.first() != 127
            val number = port.trim().takeIf { it.matches(Regex("[0-9]{1,5}")) }?.toIntOrNull()
            if (!validIp || number == null || number !in 1..65535) {
                throw WatchInstallException(InstallFailure.ADDRESS)
            }
            return AdbEndpoint(host.trim(), number)
        }
    }
}

data class WearApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val certificate: String,
    val watch: Boolean = true,
)

data class VerifiedWearApk(
    val file: File,
    val identity: WearApkIdentity,
)

data class WatchInstallPlan(
    val candidate: WearApkIdentity,
    val installed: WearApkIdentity?,
) {
    val upToDate: Boolean get() = installed?.versionCode == candidate.versionCode
}

@Suppress("ThrowsCount") // Each independent trust/version guard reports a distinct recovery action.
fun validateWearUpdate(
    candidate: WearApkIdentity,
    installed: WearApkIdentity?,
    phoneCertificate: String,
) {
    if (candidate.packageName != WATCH_PACKAGE || !candidate.watch) {
        throw WatchInstallException(InstallFailure.PACKAGE)
    }
    if (candidate.certificate != phoneCertificate ||
        installed?.certificate?.let { it != candidate.certificate } == true
    ) {
        throw WatchInstallException(InstallFailure.SIGNATURE)
    }
    if (installed != null && (installed.packageName != WATCH_PACKAGE || !installed.watch)) {
        throw WatchInstallException(InstallFailure.PACKAGE)
    }
    if (installed != null && installed.versionCode > candidate.versionCode) {
        throw WatchInstallException(InstallFailure.DOWNGRADE)
    }
}
