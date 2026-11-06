package com.quietmoments.diary

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * BiometricPrompt is optional. Errors from vendor biometric implementations
 * must never terminate the diary: the existing PIN always remains available.
 */
object BiometricDiaryAuth {
    private const val TAG = "QuietMomentsBiometric"
    private const val BIOMETRIC = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun available(context: Context): Boolean = runCatching {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.onFailure { Log.e(TAG, "Biometric availability check failed", it) }
        .getOrDefault(false)

    fun show(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        if (!available(activity)) {
            onError("Биометрия сейчас недоступна. Войди по PIN или проверь настройки телефона.")
            return
        }
        runCatching {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int, errString: CharSequence
                    ) {
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED) {
                            onCancelled()
                        } else {
                            onError("Проверка отпечатка не завершена: $errString")
                        }
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Открыть «Тихие моменты»")
                .setSubtitle("Подтверди вход отпечатком пальца")
                .setAllowedAuthenticators(BIOMETRIC)
                .setNegativeButtonText("Ввести PIN")
                .build()
            prompt.authenticate(info)
        }.onFailure {
            Log.e(TAG, "Unable to launch biometric prompt", it)
            onError("Не удалось открыть системную биометрию. Вход по PIN доступен.")
        }
    }
}
