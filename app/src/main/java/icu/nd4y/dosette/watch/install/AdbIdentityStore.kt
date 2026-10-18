package icu.nd4y.dosette.watch.install

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class AdbIdentity(
    val key: PrivateKey,
    val certificate: Certificate,
)

/** Exportable RSA is needed by SPAKE2's TLS signer; encrypted at rest with a non-exportable Keystore AES key. */
@Singleton
class AdbIdentityStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        @Synchronized
        fun load(): AdbIdentity {
            val file = File(context.noBackupFilesDir, "watch-adb-identity")
            return if (file.exists()) read(file) else generate().also { save(file, it) }
        }

        private fun generate(): AdbIdentity {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val name = X500Name("CN=Dosette watch installer")
            val now = System.currentTimeMillis()
            val builder =
                JcaX509v3CertificateBuilder(
                    name,
                    BigInteger(128, SecureRandom()),
                    Date(now - 86_400_000),
                    Date(now + 20L * 365 * 86_400_000),
                    name,
                    pair.public,
                )
            val encoded = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)).encoded
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(encoded))
            return AdbIdentity(pair.private, cert)
        }

        private fun wrappingKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            return (store.getKey(KEY_ALIAS, null) as? SecretKey)
                ?: KeyGenerator
                    .getInstance("AES", "AndroidKeyStore")
                    .apply {
                        init(
                            KeyGenParameterSpec
                                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setKeySize(256)
                                .build(),
                        )
                    }.generateKey()
        }

        private fun save(
            file: File,
            identity: AdbIdentity,
        ) {
            val clear =
                ByteArrayOutputStream().use { buffer ->
                    DataOutputStream(buffer).use { data ->
                        listOf(identity.key.encoded, identity.certificate.encoded).forEach { bytes ->
                            data.writeInt(bytes.size)
                            data.write(bytes)
                        }
                    }
                    buffer.toByteArray()
                }
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
                val encrypted = cipher.doFinal(clear)
                val staging = File(file.parentFile, "${file.name}.tmp")
                staging.outputStream().use {
                    it.write(cipher.iv)
                    it.write(encrypted)
                }
                check(staging.renameTo(file))
            } finally {
                clear.fill(0)
            }
        }

        private fun read(file: File): AdbIdentity {
            check(file.length() in 29..16_384)
            val bytes = file.readBytes()
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                }
            val clear = cipher.doFinal(bytes, 12, bytes.size - 12)
            try {
                return DataInputStream(ByteArrayInputStream(clear)).use { input ->
                    fun part(): ByteArray {
                        val size = input.readInt()
                        check(size in 1..8192)
                        return ByteArray(size).also(input::readFully)
                    }
                    val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(part()))
                    val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(part()))
                    AdbIdentity(key, cert)
                }
            } finally {
                clear.fill(0)
            }
        }

        private companion object {
            const val KEY_ALIAS = "dosette.watch.adb.wrapping"
        }
    }
