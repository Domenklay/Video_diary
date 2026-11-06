package com.quietmoments.diary

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN protects the app UI only. Local media is NOT encrypted on disk. */
class DiaryPinLock(context: Context) {
    private val prefs = context.getSharedPreferences("diary_pin", Context.MODE_PRIVATE)
    fun isEnabled(): Boolean = prefs.contains("verifier") && prefs.contains("salt")
    private fun digest(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 180_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun verify(pin: String): Boolean {
        if (!isEnabled()) return false
        val salt = Base64.decode(prefs.getString("salt", "")!!, Base64.NO_WRAP)
        val verifier = Base64.decode(prefs.getString("verifier", "")!!, Base64.NO_WRAP)
        return java.security.MessageDigest.isEqual(digest(pin, salt), verifier)
    }
    fun enable(pin: String) {
        require(pin.length in 6..16 && pin.all { it.isDigit() }) { "PIN должен содержать от 6 до 16 цифр" }
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val hash = digest(pin, salt)
        check(prefs.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("verifier", Base64.encodeToString(hash, Base64.NO_WRAP)).commit())
    }
    fun disable(pin: String): Boolean {
        if (!verify(pin)) return false
        return prefs.edit().remove("salt").remove("verifier").commit()
    }
}
