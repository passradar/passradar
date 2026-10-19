package io.github.passradar.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat
import io.github.passradar.PassradarApp
import java.util.Locale

/** The language the app is shown in: the phone's, or one picked in settings. */
enum class AppLanguage {
    System, Norwegian, English;

    /**
     * The locale to show the app in, or null to follow the phone. English keeps the phone's
     * region, so the calendar still starts the week on the day people there expect.
     */
    fun locale(phone: Locale?): Locale? = when (this) {
        System -> null
        Norwegian -> Locale.forLanguageTag("nb-NO")
        English -> Locale.forLanguageTag(phone?.country?.takeIf { it.isNotEmpty() }?.let { "en-$it" } ?: "en")
    }

    companion object {
        fun of(locale: Locale?): AppLanguage = when (locale?.language) {
            "en" -> English
            "nb", "no", "nn" -> Norwegian
            else -> System
        }
    }
}

// Android 13 and newer keep the app's language themselves (and show it under the app's
// settings, "App languages"). Older versions get it from here: each screen and notification
// is given resources in the chosen language.

fun Context.appLanguage(): AppLanguage = AppLanguage.of(
    if (Build.VERSION.SDK_INT >= 33) getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)
    else storedLocale(),
)

fun Activity.setAppLanguage(language: AppLanguage) {
    if (Build.VERSION.SDK_INT >= 33) {
        val manager = getSystemService(LocaleManager::class.java) ?: return
        val locale = language.locale(manager.systemLocales[0])
        // Android saves it and restarts the screen in the new language.
        manager.applicationLocales = if (locale != null) LocaleList(locale) else LocaleList.getEmptyLocaleList()
    } else {
        val locale = language.locale(ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0])
        languagePrefs().edit { if (locale != null) putString(KEY_LANGUAGE, locale.toLanguageTag()) else remove(KEY_LANGUAGE) }
        (application as PassradarApp).container.notifier.createChannel()
        recreate()
    }
}

/** This context, with resources in the language picked in settings where Android doesn't keep it. */
@Suppress("DEPRECATION")
fun Context.withAppLanguage(): Context {
    val chosen = storedLocale() ?: return this
    if (resources.configuration.locale == chosen) return this
    val config = Configuration().apply {
        // An empty configuration holds text size 1.0 on Android 6 and older; 0 keeps the phone's.
        fontScale = 0f
        // setLocale() would also overwrite the screen size on Android 6 and older.
        if (Build.VERSION.SDK_INT >= 24) setLocales(LocaleList(chosen)) else locale = chosen
    }
    return createConfigurationContext(config)
}

/**
 * After an update to Android 13 or newer, a language picked before moves to Android's own
 * setting. Called from the screen, so Android restarts it in that language.
 */
@RequiresApi(33)
fun Activity.moveAppLanguageToSystem() {
    if (!languagePrefs().contains(KEY_LANGUAGE)) return
    val locale = storedLocale()
    languagePrefs().edit { remove(KEY_LANGUAGE) }
    if (locale != null) getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList(locale)
}

/**
 * The language picked in settings where Android doesn't keep it: on Android 12 and older, and
 * after an update to 13 until the app is opened. One picked in Android's settings since then wins.
 */
private fun Context.storedLocale(): Locale? {
    val tag = languagePrefs().getString(KEY_LANGUAGE, null) ?: return null
    if (Build.VERSION.SDK_INT >= 33 && getSystemService(LocaleManager::class.java)?.applicationLocales?.isEmpty == false) return null
    return Locale.forLanguageTag(tag)
}

private fun Context.languagePrefs() = getSharedPreferences("language", Context.MODE_PRIVATE)

private const val KEY_LANGUAGE = "locale"
