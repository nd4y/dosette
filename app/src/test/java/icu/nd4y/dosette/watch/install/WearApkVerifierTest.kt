package icu.nd4y.dosette.watch.install

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class WearApkVerifierTest {
    @get:Rule val folder = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `embedded Wear APK has a valid cryptographic signature and required watch feature`() {
        val file = folder.newFile("watch.apk")
        context.assets.open("dosette-wear.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
        val identity = WearApkVerifier(context).verify(file)
        assertThat(identity.packageName).isEqualTo(WATCH_PACKAGE)
        assertThat(identity.watch).isTrue()
        assertThat(identity.certificate).hasLength(64)
    }

    @Suppress("NestedBlockDepth")
    @Test
    fun `repacked APK with altered dex cannot pass cryptographic verification`() {
        val file = folder.newFile("tampered.apk")
        context.assets.open("dosette-wear.apk").use { raw ->
            ZipInputStream(raw).use { source ->
                ZipOutputStream(file.outputStream()).use { target ->
                    while (true) {
                        val entry = source.nextEntry ?: break
                        val bytes = source.readBytes()
                        if (entry.name == "classes.dex") bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
                        target.putNextEntry(ZipEntry(entry.name))
                        target.write(bytes)
                        target.closeEntry()
                    }
                }
            }
        }
        val failure = assertThrows(WatchInstallException::class.java) { WearApkVerifier(context).verify(file) }
        assertThat(failure.reason).isEqualTo(InstallFailure.SIGNATURE)
    }
}
