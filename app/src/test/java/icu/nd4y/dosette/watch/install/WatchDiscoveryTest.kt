package icu.nd4y.dosette.watch.install

import android.net.nsd.NsdServiceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchDiscoveryTest {
    @Test fun `dual stack advertisement selects IPv4 and its actual service port`() {
        val info =
            NsdServiceInfo().apply {
                hostAddresses = listOf(InetAddress.getByName("fe80::1"), InetAddress.getByName("192.168.1.25"))
                port = 33445
            }
        assertThat(nsdEndpoint(info)).isEqualTo(AdbEndpoint("192.168.1.25", 33445))
    }

    @Test fun `invalid and unsupported endpoints never become install targets`() {
        val info =
            NsdServiceInfo().apply {
                hostAddresses = listOf(InetAddress.getByName("127.0.0.1"))
                port = 0
            }
        assertThat(nsdEndpoint(info)).isNull()
    }
}
