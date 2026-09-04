package com.example.ai_camera.settings

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.example.ai_camera.R
import java.util.Locale

enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    /** Follow whatever the device is set to. */
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    TRADITIONAL_CHINESE("zh-TW", R.string.language_zh_tw),
    JAPANESE("ja", R.string.language_ja);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
    }
}

/**
 * Per-app language, stored locally and applied by overriding the activity's base context.
 *
 * This deliberately avoids AppCompat: the app is Compose-only on a plain ComponentActivity with a
 * platform theme, and pulling in AppCompat purely for `setApplicationLocales` would force both an
 * activity base class and a theme change. The trade-off is no Android 13+ system per-app language
 * integration - the choice lives only inside this app.
 */
object LocaleSettings {
    private const val PREFS = "app_settings"
    private const val KEY_LANGUAGE = "language_tag"

    fun current(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppLanguage.fromTag(tag)
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag)
            .apply()
    }

    /** Returns a context whose resources resolve in the selected language. */
    fun wrap(context: Context): Context {
        val tag = current(context).tag ?: return context
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
