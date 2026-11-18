package io.github.jsnjack.wallet.platform

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/** Fingerprint, face or screen-lock check before a locked card's code is shown. */
object Biometrics {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** True when the phone has a screen lock or enrolled biometrics to check against. */
    fun available(context: Context): Boolean =
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the system prompt; exactly one of the callbacks runs unless the user dismisses it. */
    fun authenticate(activity: Activity, title: String, onSuccess: () -> Unit, onError: (CharSequence) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    ) {
                        onError(errString)
                    }
                }
            },
        )
    }
}
