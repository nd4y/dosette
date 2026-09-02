package icu.nd4y.dosette.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val plain = "schema_version: 1\nprofiles: []\n".toByteArray()

    @Test
    fun `round trip restores the plaintext`() {
        val sealed = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        val restored = BackupCrypto.decrypt(sealed, "correct horse".toCharArray())
        assertThat(restored).isEqualTo(plain)
    }

    @Test
    fun `encrypted payload is recognized, plaintext is not`() {
        val sealed = BackupCrypto.encrypt(plain, "pw".toCharArray())
        assertThat(BackupCrypto.isEncrypted(sealed)).isTrue()
        assertThat(BackupCrypto.isEncrypted(plain)).isFalse()
    }

    @Test
    fun `two encryptions of the same payload differ`() {
        val a = BackupCrypto.encrypt(plain, "pw".toCharArray())
        val b = BackupCrypto.encrypt(plain, "pw".toCharArray())
        assertThat(a).isNotEqualTo(b) // fresh salt and nonce every time
    }

    @Test
    fun `wrong password is rejected`() {
        val sealed = BackupCrypto.encrypt(plain, "right".toCharArray())
        assertThrows(BackupPasswordException::class.java) {
            BackupCrypto.decrypt(sealed, "wrong".toCharArray())
        }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val sealed = BackupCrypto.encrypt(plain, "pw".toCharArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        assertThrows(BackupPasswordException::class.java) {
            BackupCrypto.decrypt(sealed, "pw".toCharArray())
        }
    }

    @Test
    fun `truncated file is rejected with a clear error`() {
        val sealed = BackupCrypto.encrypt(plain, "pw".toCharArray())
        assertThrows(BackupFormatException::class.java) {
            BackupCrypto.decrypt(sealed.copyOfRange(0, 20), "pw".toCharArray())
        }
    }
}
