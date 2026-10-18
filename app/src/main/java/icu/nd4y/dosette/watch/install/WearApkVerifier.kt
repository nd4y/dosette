package icu.nd4y.dosette.watch.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class WearApkVerifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun bundled(): VerifiedWearApk {
            val file = File(workDirectory(), "bundled.apk")
            context.assets.open("dosette-wear.apk").use { source ->
                file.outputStream().use { target ->
                    val bytes = source.copyTo(target)
                    if (bytes !in 1..MAX_WEAR_APK_BYTES) throw WatchInstallException(InstallFailure.PACKAGE)
                }
            }
            val candidate = verify(file)
            validateWearUpdate(candidate, null, phoneCertificate())
            return VerifiedWearApk(file, candidate)
        }

        @Suppress("ThrowsCount", "TooGenericExceptionCaught")
        fun verify(file: File): WearApkIdentity {
            if (file.length() !in 1..MAX_WEAR_APK_BYTES) throw WatchInstallException(InstallFailure.PACKAGE)
            val signature =
                try {
                    ApkVerifier
                        .Builder(file)
                        .setMinCheckedPlatformVersion(30)
                        .build()
                        .verify()
                } catch (_: Exception) {
                    throw WatchInstallException(InstallFailure.SIGNATURE)
                }
            if (!signature.isVerified || signature.signerCertificates.size != 1) {
                throw WatchInstallException(InstallFailure.SIGNATURE)
            }
            @Suppress("DEPRECATION")
            val info =
                context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_CONFIGURATIONS)
                    ?: throw WatchInstallException(InstallFailure.PACKAGE)
            val watch =
                info.reqFeatures.orEmpty().any {
                    it.name == PackageManager.FEATURE_WATCH && it.flags and 1 != 0
                }
            return WearApkIdentity(
                info.packageName,
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
                info.versionName.orEmpty(),
                sha256(signature.signerCertificates.single().encoded),
                watch,
            )
        }

        @Suppress("DEPRECATION")
        fun phoneCertificate(): String {
            val flags =
                if (Build.VERSION.SDK_INT >= 28) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
            val info = context.packageManager.getPackageInfo(context.packageName, flags)
            val certificates =
                if (Build.VERSION.SDK_INT >= 28) {
                    info.signingInfo?.apkContentsSigners
                } else {
                    info.signatures
                }
            if (certificates?.size != 1) throw WatchInstallException(InstallFailure.SIGNATURE)
            return sha256(certificates.single().toByteArray())
        }

        fun workDirectory(): File = File(context.cacheDir, "watch-install").apply { mkdirs() }
    }

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
