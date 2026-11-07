package montafra.beam

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import org.xmlpull.v1.XmlPullParser

// In-app language pick for API < 33, which has no per-app language page in system settings. API 33+
// uses that page (LocaleManager), so the "appLanguage" pref is never read there.

private const val androidNs = "http://schemas.android.com/apk/res/android"

/**
 * The locale-only configuration override for the in-app language pick, or null to follow the
 * system. Read with MODE_MULTI_PROCESS because :batteryStatus applies it too.
 */
fun appLanguageOverride(context: Context): Configuration? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return null
    val tag = context.getSharedPreferences(settingsName, Context.MODE_MULTI_PROCESS)
        .getString("appLanguage", null)
    if (tag.isNullOrEmpty()) return null
    // A bare Configuration has every field undefined, so applying it overrides the locale only.
    return Configuration().apply { setLocales(LocaleList.forLanguageTags(tag)) }
}

/** This context with the in-app language applied, for code that resolves strings outside the activity. */
fun Context.withAppLanguage(): Context =
    appLanguageOverride(this)?.let { createConfigurationContext(it) } ?: this

/** The language tags the app ships, read from the manifest's locales_config so the list lives in one place. */
fun supportedAppLanguages(context: Context): List<String> = buildList {
    context.resources.getXml(R.xml.locales_config).use { parser ->
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(androidNs, "name")?.let(::add)
            }
        }
    }
}

/**
 * Hands an in-app pick over to the system once the device is on API 33+, where the pref is no
 * longer read - otherwise an OS upgrade would silently drop the user back to the system language.
 */
fun migrateAppLanguage(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val prefs = context.getSharedPreferences(settingsName, Context.MODE_PRIVATE)
    val tag = prefs.getString("appLanguage", null)
    if (tag.isNullOrEmpty()) return
    val localeManager = context.getSystemService(LocaleManager::class.java)
    if (localeManager.applicationLocales.isEmpty) {
        localeManager.applicationLocales = LocaleList.forLanguageTags(tag)
    }
    prefs.edit().remove("appLanguage").apply()
}
