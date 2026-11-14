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
    /**
     * Persistent UI-entry throttle. The attempt counter survives process
     * death and ordinary app restarts; a temporary lock clears automatically.
     * Biometrics remain an independent Android-verified recovery path.
     */
    @Synchronized fun unlockDelayRemainingMs(): Long {
        val now = System.currentTimeMillis()
        val until = prefs.getLong("entry_lockout_until", 0L)
        if (until <= now) {
            if (until != 0L) prefs.edit().remove("entry_lockout_until").apply()
            return 0L
        }
        return until - now
    }

    @Synchronized fun verifyForUnlock(pin: String): Boolean {
        if (!isEnabled() || unlockDelayRemainingMs() > 0L) return false
        val valid = verify(pin)
        if (valid) {
            prefs.edit().remove("entry_failures").remove("entry_lockout_until").commit()
            return true
        }
        val failures = prefs.getInt("entry_failures", 0) + 1
        val edit = prefs.edit()
        if (failures >= 5) {
            edit.putInt("entry_failures", 0)
                .putLong("entry_lockout_until", System.currentTimeMillis() + 2L * 60_000L)
        } else {
            edit.putInt("entry_failures", failures)
        }
        check(edit.commit()) { "Не удалось сохранить защиту PIN" }
        return false
    }

    fun enable(pin: String) {
        require(pin.length in 6..16 && pin.all { it.isDigit() }) { "PIN должен содержать от 6 до 16 цифр" }
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val hash = digest(pin, salt)
        check(prefs.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("verifier", Base64.encodeToString(hash, Base64.NO_WRAP))
            .remove("entry_failures").remove("entry_lockout_until").commit())
    }
    /** Biometrics can be opted in only by someone who already knows the PIN. */
    fun isBiometricEnabled(): Boolean =
        isEnabled() && prefs.getBoolean("biometric_login", false)

    fun setBiometricEnabled(enabled: Boolean, currentPin: String): Boolean {
        if (!verify(currentPin)) return false
        return prefs.edit().putBoolean("biometric_login", enabled).commit()
    }

    fun disable(pin: String): Boolean {
        if (!verify(pin)) return false
        return prefs.edit().remove("salt").remove("verifier")
            .remove("biometric_login").remove("entry_failures")
            .remove("entry_lockout_until").commit()
    }
}
