package montafra.beam

import android.Manifest.permission
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import montafra.beam.ui.AlarmsSettingsScreen
import montafra.beam.ui.ColorPickerScreen
import montafra.beam.ui.LanguageSettingsScreen
import montafra.beam.ui.LocalSilentHaptics
import montafra.beam.ui.LocalSoundEnabled
import montafra.beam.ui.LocalTapHaptics
import montafra.beam.ui.MainScreen
import montafra.beam.ui.NoOpHapticFeedback
import montafra.beam.ui.NotificationSettingsScreen
import montafra.beam.ui.PredictiveNavHost
import montafra.beam.ui.SettingsScreen
import montafra.beam.ui.TapFeedback
import montafra.beam.ui.ThemeSettingsScreen
import montafra.beam.ui.WorkaroundsSettingsScreen
import montafra.beam.ui.rememberBeamNavController
import montafra.beam.ui.theme.BeamTheme
import montafra.beam.ui.theme.rememberThemePrefs

const val namespace = "montafra.beam"
const val batteryDataReq = "$namespace.battery-data-req"
const val batteryDataResp = "$namespace.battery-data-resp"
const val intervalMs = 1_250L
const val noteChannelId = "$namespace.status.v6"
const val noteId = 1
const val alarmChannelId = "$namespace.alarms.v1"
const val alarmLowNoteId = 2
const val alarmHighNoteId = 3
const val alarmTempNoteId = 4
// Shared by StatusService and AlarmsSettingsScreen so the service can never disagree with the
// value the settings screen displays.
const val alarmLowDefaultThreshold = 20
const val alarmHighDefaultThreshold = 85
const val alarmTempDefaultThreshold = 40
const val alarmRepeatDefaultMin = 15
const val settingsName = "settings"
const val settingsUpdateInd = "$namespace.settings-update-ind"

val LocalHapticsEnabled = staticCompositionLocalOf { true }

class MainActivity : ComponentActivity() {
    enum class Perm { Granted, Denied, NotAsked }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }
    private var bound = false

    private fun getPerm(name: String): Perm {
        val settings = getSharedPreferences(settingsName, MODE_PRIVATE)
        return when {
            checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED -> Perm.Granted
            settings.getBoolean("${name}_ASKED", false) -> Perm.Denied
            else -> Perm.NotAsked
        }
    }

    private fun requestPerm(name: String) {
        getSharedPreferences(settingsName, MODE_PRIVATE)
            .edit().putBoolean("${name}_ASKED", true).apply()
        requestPermissions(arrayOf(name), 0)
    }

    private fun startStatusService() {
        val intent = Intent(this, StatusService::class.java)
        if (getSharedPreferences(settingsName, MODE_PRIVATE).getBoolean("notificationEnabled", true)) {
            startForegroundService(intent)
        }
        if (!bound) {
            bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun syncNotificationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences(settingsName, MODE_PRIVATE)
                .edit().putBoolean("notificationEnabled", false).apply()
            BeamNotificationTileService.requestRefresh(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissions.contains(permission.POST_NOTIFICATIONS)) {
            syncNotificationEnabled()
            startStatusService()
        }
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // The in-app language pick (API < 33 only). Must land before anything touches resources.
        appLanguageOverride(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The default SystemBarStyle.auto() writes a scrim into navigationBarColor on API 28 and
        // turns isNavigationBarContrastEnforced back on from API 29, overriding what
        // values-v29/themes.xml asks for. A non-auto style keeps both off, so the nav bar stays
        // fully transparent - gesture pill / 3-button icons sit straight on the content.
        // BeamTheme owns the bar icon appearance, so dark() here only holds until first composition.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        if (getPerm(permission.POST_NOTIFICATIONS) == Perm.NotAsked) {
            requestPerm(permission.POST_NOTIFICATIONS)
            // startStatusService() is called from onRequestPermissionsResult once the user responds
        } else {
            syncNotificationEnabled()
            startStatusService()
        }

        setContent {
            val themePrefs by rememberThemePrefs()
            val hapticsEnabled = remember {
                mutableStateOf(getSharedPreferences(settingsName, MODE_PRIVATE).getBoolean("hapticsEnabled", true))
            }
            val soundEnabled = remember {
                mutableStateOf(getSharedPreferences(settingsName, MODE_PRIVATE).getBoolean("soundEnabled", true))
            }
            DisposableEffect(Unit) {
                val prefs = getSharedPreferences(settingsName, MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    when (key) {
                        "hapticsEnabled" -> hapticsEnabled.value = p.getBoolean(key, true)
                        "soundEnabled" -> soundEnabled.value = p.getBoolean(key, true)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val realHaptic = LocalHapticFeedback.current
            val view = LocalView.current
            // The app's own tap call sites read LocalTapHaptics and get the platform tap sound on
            // top of the haptic; the two are gated separately. LocalHapticFeedback itself is only
            // gated, never given the sound channel: Compose/Material internals (text-selection
            // handles, stepped-slider ticks) fire through it, and each of those would click.
            val tapFeedback = remember(realHaptic, view, hapticsEnabled.value, soundEnabled.value) {
                TapFeedback(
                    haptics = realHaptic.takeIf { hapticsEnabled.value },
                    view = view.takeIf { soundEnabled.value },
                )
            }
            val gatedHaptic = if (hapticsEnabled.value) realHaptic else NoOpHapticFeedback
            BeamTheme(themePrefs) {
                CompositionLocalProvider(
                    LocalHapticFeedback provides gatedHaptic,
                    LocalTapHaptics provides tapFeedback,
                    LocalHapticsEnabled provides hapticsEnabled.value,
                    LocalSoundEnabled provides soundEnabled.value,
                    LocalSilentHaptics provides gatedHaptic,
                ) {
                val navController = rememberBeamNavController(startRoute = "main")
                PredictiveNavHost(navController) { route ->
                    when (route) {
                        "main" -> MainScreen(navController)
                        "settings" -> SettingsScreen(navController)
                        "settings/theme" -> ThemeSettingsScreen(navController)
                        "settings/theme/color" -> ColorPickerScreen(navController)
                        "settings/language" -> LanguageSettingsScreen(navController)
                        "settings/notification" -> NotificationSettingsScreen(navController)
                        "settings/alarms" -> AlarmsSettingsScreen(navController)
                        "settings/workarounds" -> WorkaroundsSettingsScreen(navController)
                    }
                }
                } // CompositionLocalProvider
            }
        }
    }
}
