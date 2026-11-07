package montafra.beam

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class BeamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        migrateAppLanguage(this)
        applyNightMode(getSharedPreferences(settingsName, MODE_PRIVATE).getString("themeMode", "system"))
    }
}

fun applyNightMode(mode: String?) {
    AppCompatDelegate.setDefaultNightMode(
        when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark", "oled" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}
