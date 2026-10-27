package com.rishi.kiranaledger

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The three app languages. [tag] matches the res/values-* folders. */
enum class AppLanguage(val tag: String, val label: String) {
    HINGLISH("hi-Latn", "Hinglish"),
    ENGLISH("en", "English"),
    HINDI("hi", "हिंदी");

    fun apply() = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

    companion object {
        /** Hinglish is the shopkeeper's default, whatever language the phone itself uses. */
        fun applyDefaultIfUnset() {
            if (AppCompatDelegate.getApplicationLocales().isEmpty) HINGLISH.apply()
        }
    }
}
