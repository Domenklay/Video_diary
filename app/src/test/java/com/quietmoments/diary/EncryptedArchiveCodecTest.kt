package com.quietmoments.diary

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

class EncryptedArchiveCodecTest {
    private val password = "Длинная фраза пароль 2026!"

    private fun encrypt(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { encrypted ->
            assertEquals(bytes.size.toLong(), EncryptedArchiveCodec.seal(
                ByteArrayInputStream(bytes), encrypted, password.toCharArray()))
        }.toByteArray()

    private fun decode(bytes: ByteArray, pass: String = password): ByteArray =
        ByteArrayOutputStream().also { out ->
            EncryptedArchiveCodec.open(ByteArrayInputStream(bytes), out, pass.toCharArray())
        }.toByteArray()

    @Test fun roundTripAcrossManyAuthenticatedChunks() {
        val source = ByteArray(64 * 1024 * 3 + 9).also { SecureRandom().nextBytes(it) }
        val encoded = encrypt(source)
        assertTrue(EncryptedArchiveCodec.isEncrypted(ByteArrayInputStream(encoded)))
        assertArrayEquals(source, decode(encoded))
    }

    @Test fun emptyArchiveHasAuthenticatedEnd() {
        val encoded = encrypt(ByteArray(0))
        assertArrayEquals(ByteArray(0), decode(encoded))
    }

    @Test fun wrongPasswordNeverAuthenticates() {
        val encoded = encrypt("private memories".toByteArray())
        try {
            decode(encoded, "a completely wrong 2026 password")
            fail("Wrong password must fail")
        } catch (expected: IllegalArgumentException) { }
    }

    @Test fun tamperedCiphertextRejected() {
        val encoded = encrypt("My personal diary".toByteArray())
        encoded[38] = (encoded[38].toInt() xor 0x10).toByte()
        try { decode(encoded); fail("Modified ciphertext must fail") }
        catch (expected: Exception) { }
    }

    @Test fun truncatingEndMarkerRejected() {
        val encoded = encrypt("Some diary".toByteArray())
        try { decode(encoded.copyOf(encoded.size - 20)); fail("Truncation accepted") }
        catch (expected: Exception) { }
    }

    @Test fun appendedDataRejected() {
        val encoded = encrypt("Diary text".toByteArray()) + byteArrayOf(2, 4, 6)
        try { decode(encoded); fail("Trailing garbage accepted") }
        catch (expected: Exception) { }
    }

    @Test fun shortPasswordRejected() {
        try {
            encrypt(ByteArray(0)).let {
                EncryptedArchiveCodec.open(ByteArrayInputStream(it),
                    ByteArrayOutputStream(), "short".toCharArray())
            }
            fail("Short password accepted")
        } catch (expected: IllegalArgumentException) { }
    }
}
