package com.quietmoments.diary

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable password-encrypted QuietMoments backup (.qmb).
 *
 * Header: 8-byte "QMBENC01", random 16-byte PBKDF2 salt, random 8-byte
 * nonce prefix. Frames: [int32 ciphertext-length][AES-256-GCM ciphertext].
 * Nonce = random prefix || monotonically increasing 32-bit frame index.
 * Each frame authenticates the header, index, and terminal/nonterminal marker.
 * A final authenticated empty frame is REQUIRED to reject truncated backups.
 *
 * This codec authenticates EACH frame before writing decrypted data. Callers
 * MUST only use the decrypted staging file after open() completes successfully,
 * followed by BackupArchive.inspect() and only then importBackup().
 *
 * Uses a bounded 64 KiB buffer and is suitable for large video backups.
 */
object EncryptedArchiveCodec {
    private val MAGIC = "QMBENC01".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val PREFIX_BYTES = 8
    private const val HEADER_BYTES = 8 + SALT_BYTES + PREFIX_BYTES
    private const val CHUNK_BYTES = 64 * 1024
    private const val TAG_BYTES = 16
    private const val KDF_ITERATIONS = 210_000
    private const val MAX_CHUNKS = 40_000
    private const val MAX_PLAINTEXT = 2L * 1024L * 1024L * 1024L + 8L * 1024L * 1024L

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 12) { "Для защищённой копии нужен пароль не короче 12 символов" }
        val spec = PBEKeySpec(password, salt, KDF_ITERATIONS, 256)
        val derived = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return try { SecretKeySpec(derived, "AES") } finally { derived.fill(0) }
    }

    private fun cipher(
        mode: Int, secret: SecretKeySpec, prefix: ByteArray,
        index: Int, header: ByteArray, terminal: Boolean
    ): Cipher {
        require(index >= 0 && index < MAX_CHUNKS) { "Слишком много блоков архива" }
        val nonce = ByteBuffer.allocate(12).put(prefix).putInt(index).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, secret, GCMParameterSpec(128, nonce))
            val aad = ByteBuffer.allocate(header.size + 5)
                .put(header).putInt(index).put(if (terminal) 1.toByte() else 0.toByte()).array()
            updateAAD(aad)
        }
    }

    /** Does NOT close the caller's streams; does clear the supplied password. */
    fun seal(input: InputStream, output: OutputStream, password: CharArray): Long {
        try {
            val rng = SecureRandom()
            val header = MAGIC + ByteArray(SALT_BYTES).also(rng::nextBytes) +
                ByteArray(PREFIX_BYTES).also(rng::nextBytes)
            val secret = derive(password, header.copyOfRange(8, 24))
            val prefix = header.copyOfRange(24, HEADER_BYTES)
            val out = DataOutputStream(output)
            out.write(header)
            val buffer = ByteArray(CHUNK_BYTES)
            var total = 0L
            var index = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                total += n
                require(total <= MAX_PLAINTEXT) { "Слишком большой архив" }
                val encrypted = cipher(Cipher.ENCRYPT_MODE, secret, prefix, index,
                    header, false).doFinal(buffer, 0, n)
                out.writeInt(encrypted.size)
                out.write(encrypted)
                index++
            }
            val last = cipher(Cipher.ENCRYPT_MODE, secret, prefix, index,
                header, true).doFinal(ByteArray(0))
            out.writeInt(last.size)
            out.write(last)
            out.flush()
            return total
        } finally { password.fill('\u0000') }
    }

    /** No plaintext may be used by the caller until this returns without exception. */
    fun open(input: InputStream, output: OutputStream, password: CharArray): Long {
        try {
            val data = DataInputStream(input)
            val header = ByteArray(HEADER_BYTES)
            data.readFully(header)
            require(Arrays.equals(header.copyOfRange(0, 8), MAGIC)) {
                "Это не зашифрованная копия QuietMoments (.qmb)"
            }
            val secret = derive(password, header.copyOfRange(8, 24))
            val prefix = header.copyOfRange(24, HEADER_BYTES)
            var index = 0
            var total = 0L
            while (true) {
                require(index < MAX_CHUNKS) { "В архиве слишком много блоков" }
                val size = try { data.readInt() } catch (e: EOFException) {
                    throw IllegalArgumentException("Архив обрезан: отсутствует завершающий блок", e)
                }
                require(size in TAG_BYTES..(CHUNK_BYTES + TAG_BYTES)) {
                    "Повреждена структура зашифрованного архива"
                }
                val ciphertext = ByteArray(size)
                data.readFully(ciphertext)
                val terminal = size == TAG_BYTES
                val plain = try {
                    cipher(Cipher.DECRYPT_MODE, secret, prefix, index, header,
                        terminal).doFinal(ciphertext)
                } catch (e: javax.crypto.AEADBadTagException) {
                    throw IllegalArgumentException("Неверный пароль или архив повреждён", e)
                }
                if (terminal) {
                    require(plain.isEmpty() && data.read() == -1) {
                        "После последнего блока обнаружены лишние данные"
                    }
                    output.flush()
                    return total
                }
                require(plain.isNotEmpty()) { "Обнаружен пустой блок архива" }
                total += plain.size
                require(total <= MAX_PLAINTEXT) { "Слишком большой архив" }
                output.write(plain)
                index++
            }
        } finally { password.fill('\u0000') }
    }

    /** Inspect only a fixed header, never parse the ZIP content before authentication. */
    fun isEncrypted(input: InputStream): Boolean {
        val magic = ByteArray(MAGIC.size)
        var i = 0
        while (i < magic.size) {
            val n = input.read(magic, i, magic.size - i)
            if (n < 0) return false
            if (n == 0) continue
            i += n
        }
        return Arrays.equals(magic, MAGIC)
    }
}
