package io.github.jsnjack.wallet.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExportCryptoTest {
    // Low iteration count keeps the tests fast; the file records whichever count was used.
    private val iterations = 1_000
    private val plain = "{\"cards\":[]}".encodeToByteArray()

    @Test
    fun decryptsWithTheSamePassword() {
        val file = ExportCrypto.encrypt(plain, "correct horse".toCharArray(), iterations)
        assertArrayEquals(plain, ExportCrypto.decrypt(file, "correct horse".toCharArray()))
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPasswordIsReported() {
        val file = ExportCrypto.encrypt(plain, "correct horse".toCharArray(), iterations)
        ExportCrypto.decrypt(file, "battery staple".toCharArray())
    }

    @Test(expected = NotAnExportException::class)
    fun otherFilesAreRejected() {
        ExportCrypto.decrypt("PK\u0003\u0004 a zip file".encodeToByteArray(), "x".toCharArray())
    }

    @Test(expected = WrongPasswordException::class)
    fun tamperedFileFailsAuthentication() {
        val file = ExportCrypto.encrypt(plain, "pw".toCharArray(), iterations)
        file[file.size - 1] = (file[file.size - 1].toInt() xor 1).toByte()
        ExportCrypto.decrypt(file, "pw".toCharArray())
    }

    @Test
    fun ciphertextDoesNotContainThePlainText() {
        val file = ExportCrypto.encrypt(plain, "pw".toCharArray(), iterations)
        assertFalse(file.decodeToString(throwOnInvalidSequence = false).contains("cards"))
    }

    @Test(expected = NotAnExportException::class)
    fun aHeaderAskingForBillionsOfRoundsIsRejected() {
        val file = ExportCrypto.encrypt("[]".encodeToByteArray(), "secret".toCharArray(), iterations = 1_000)
        // The count follows the 4-byte magic and the version byte.
        java.nio.ByteBuffer.wrap(file).putInt(5, Int.MAX_VALUE)
        ExportCrypto.decrypt(file, "secret".toCharArray())
    }
}
