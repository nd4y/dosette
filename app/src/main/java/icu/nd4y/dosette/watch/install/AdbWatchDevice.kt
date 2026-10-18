package icu.nd4y.dosette.watch.install

import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.AdbStream
import java.io.File
import java.io.OutputStream

internal class AdbWatchDevice(
    private val connection: AdbConnection,
) : WatchDevice {
    override fun command(command: String): String =
        connection.open("exec:$command").use { stream ->
            readBounded(stream, 64 * 1024).toString(Charsets.UTF_8)
        }

    override fun pullApk(
        path: String,
        destination: File,
    ) {
        connection.open("exec:cat $path").use { stream ->
            destination.outputStream().use { target -> copyApk(stream, target) }
        }
    }

    private fun copyApk(
        stream: AdbStream,
        target: OutputStream,
    ) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = stream.read(buffer, 0, buffer.size)
            if (count < 0) break
            total += count
            if (total > MAX_WEAR_APK_BYTES) throw WatchInstallException(InstallFailure.PACKAGE)
            target.write(buffer, 0, count)
        }
    }

    override fun writeApk(
        session: Int,
        apk: File,
        progress: (Int) -> Unit,
    ) {
        val size = apk.length()
        connection.open("exec:cmd package install-write -S $size $session base.apk -").use { stream ->
            sendApk(stream, apk, progress)
            stream.flush()
            val result = readBounded(stream, 4096).toString(Charsets.UTF_8).trim()
            if (!result.startsWith("Success")) throw WatchInstallException(InstallFailure.INSTALL)
        }
    }

    private fun sendApk(
        stream: AdbStream,
        apk: File,
        progress: (Int) -> Unit,
    ) {
        apk.inputStream().use { source ->
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                stream.write(buffer, 0, count)
                sent += count
                progress((sent * 100 / apk.length()).toInt())
            }
        }
    }

    private fun readBounded(
        stream: AdbStream,
        limit: Int,
    ): ByteArray {
        val buffer = ByteArray(limit + 1)
        var position = 0
        while (position <= limit) {
            val count = stream.read(buffer, position, buffer.size - position)
            if (count < 0) return buffer.copyOf(position)
            position += count
        }
        throw WatchInstallException(InstallFailure.CONNECTION)
    }
}
