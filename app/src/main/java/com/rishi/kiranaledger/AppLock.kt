package com.rishi.kiranaledger

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock using the phone's own fingerprint / face / PIN / pattern, so the shopkeeper has nothing new to remember.
 * The app locks when opened and when it comes back after more than a minute in the background.
 */
object AppLock {
    private const val PREFS = "lock"
    private const val RELOCK_AFTER_MS = 60_000L
    private var unlockedUntilBackgroundAt: Long? = null

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()

    /** Fingerprint/face, or the phone's PIN/pattern as a fallback. */
    private val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True if the phone has a screen lock or fingerprint set up (needed for the app lock to work). */
    fun canUse(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS ||
            // Android 8–10 can't combine biometric + PIN in canAuthenticate; check biometrics alone.
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS)

    fun onBackground() {
        unlockedUntilBackgroundAt = System.currentTimeMillis()
    }

    /** Whether the lock screen should show now. */
    fun needsUnlock(context: Context, alreadyUnlocked: Boolean): Boolean {
        if (!isEnabled(context)) return false
        val left = unlockedUntilBackgroundAt ?: return !alreadyUnlocked
        return !alreadyUnlocked || System.currentTimeMillis() - left > RELOCK_AFTER_MS
    }

    fun prompt(activity: FragmentActivity, title: String, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlockedUntilBackgroundAt = null
                    onUnlocked()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.setAllowedAuthenticators(authenticators)
        } else {
            @Suppress("DEPRECATION")
            info.setDeviceCredentialAllowed(true)
        }
        prompt.authenticate(info.build())
    }
}
