package io.github.jsnjack.wallet.core

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The password does not open this export file. */
class WrongPasswordException : Exception("wrong export password")

/** The file is not a wallet export, or it is damaged. */
class NotAnExportException(message: String) : Exception(message)

/**
 * Password encryption for export files: AES-256-GCM with a key derived by
 * PBKDF2-HMAC-SHA256. The header holds the iteration count, so later
 * versions can raise it and still read older files.
 */
object ExportCrypto {
    /** PBKDF2 iterations for new files (OWASP 2023 guidance for HMAC-SHA256). */
    const val DEFAULT_ITERATIONS = 600_000

    private val MAGIC = "WLTX".toByteArray(Charsets.US_ASCII)
    private const val FILE_VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + IV_BYTES

    // Room for later versions to raise the count tenfold.
    private const val MAX_ITERATIONS = 10 * DEFAULT_ITERATIONS

    /** Encrypts [plain] with [password]; the result is a complete export file. */
    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        val sealed = cipher.doFinal(plain)
        return ByteBuffer.allocate(HEADER_BYTES + sealed.size)
            .put(MAGIC).put(FILE_VERSION).putInt(iterations).put(salt).put(iv).put(sealed)
            .array()
    }

    /** Decrypts an export file made by [encrypt]. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size <= HEADER_BYTES || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw NotAnExportException("file does not start with the wallet export header")
        }
        val buffer = ByteBuffer.wrap(file, MAGIC.size, file.size - MAGIC.size)
        val version = buffer.get()
        if (version != FILE_VERSION) throw NotAnExportException("export file version $version is not supported")
        val iterations = buffer.getInt()
        // A damaged header could ask for billions of rounds, keeping the import busy for an hour.
        if (iterations !in 1..MAX_ITERATIONS) throw NotAnExportException("export file header is damaged")
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val sealed = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        return try {
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: GeneralSecurityException) {
            throw NotAnExportException("export file could not be decrypted: ${e.message}")
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
