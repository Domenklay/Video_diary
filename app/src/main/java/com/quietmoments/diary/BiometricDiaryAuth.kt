package com.quietmoments.diary

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Explicit biometric gesture for the existing PIN gate; PIN remains the fallback.
 * This authorizes UI access only; on-disk photos are not encrypted by this gate.
 */
object BiometricDiaryAuth {
    private val biometric = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(biometric) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun show(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!available(activity)) {
            onError("На устройстве нет настроенной биометрии. Введи PIN.")
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancellation is not a failed PIN attempt; leave fallback visible.
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        onError(errString.toString())
                    }
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Открыть «Тихие моменты»")
            .setSubtitle("Подтверди вход отпечатком пальца или другой биометрией")
            .setAllowedAuthenticators(biometric)
            .setNegativeButtonText("Ввести PIN")
            .build()
        prompt.authenticate(info)
    }
}
