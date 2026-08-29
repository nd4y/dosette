package icu.nd4y.dosette.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password encryption of the backup file: PBKDF2-HMAC-SHA256 (600k
 * iterations, OWASP-grade) derives an AES-256 key, AES-GCM seals the
 * payload. Binary layout: MAGIC | salt(16) | nonce(12) | ciphertext+tag.
 *
 * Passkeys were considered and rejected: a passkey is an authentication
 * credential, not a portable file-encryption key — without a server holding
 * the other half there is no deterministic way to re-derive the key on a
 * new device (the FIDO2 PRF extension exists but Android credential-manager
 * support is too patchy to gate medical data on).
 */
object BackupCrypto {
    private val MAGIC = "DSTENC01".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 600_000

    fun isEncrypted(data: ByteArray): Boolean =
        data.size > MAGIC.size && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
    ): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val sealed = cipher.doFinal(plain)
        return MAGIC + salt + nonce + sealed
    }

    fun decrypt(
        data: ByteArray,
        password: CharArray,
    ): ByteArray {
        val saltStart = MAGIC.size
        val nonceStart = saltStart + SALT_BYTES
        val payloadStart = nonceStart + NONCE_BYTES
        if (!isEncrypted(data) || data.size <= payloadStart) {
            throw BackupFormatException("Not a valid encrypted Dosette backup")
        }

        val salt = data.copyOfRange(saltStart, nonceStart)
        val nonce = data.copyOfRange(nonceStart, payloadStart)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return runCatching { cipher.doFinal(data.copyOfRange(payloadStart, data.size)) }
            .getOrElse { throw BackupFormatException("Wrong password or corrupted file", it) }
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        spec.clearPassword()
        return SecretKeySpec(key.encoded, "AES")
    }
}
