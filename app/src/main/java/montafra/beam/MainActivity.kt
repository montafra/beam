package montafra.beam

import android.Manifest.permission
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import montafra.beam.ui.MainScreen
import montafra.beam.ui.NotificationSettingsScreen
import montafra.beam.ui.SettingsScreen
import montafra.beam.ui.ThemeSettingsScreen
import montafra.beam.ui.WorkaroundsSettingsScreen
import montafra.beam.ui.theme.BeamTheme
import montafra.beam.ui.theme.rememberThemePrefs

const val namespace = "montafra.beam"
const val batteryDataReq = "$namespace.battery-data-req"
const val batteryDataResp = "$namespace.battery-data-resp"
const val intervalMs = 1_250L
const val noteChannelId = "$namespace.status.v6"
const val noteId = 1
const val settingsName = "settings"
const val settingsUpdateInd = "$namespace.settings-update-ind"

private object NoOpHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            DisposableEffect(Unit) {
                val prefs = getSharedPreferences(settingsName, MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    if (key == "hapticsEnabled") hapticsEnabled.value = p.getBoolean(key, true)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val realHaptic = LocalHapticFeedback.current
            BeamTheme(themePrefs) {
                CompositionLocalProvider(
                    LocalHapticFeedback provides if (hapticsEnabled.value) realHaptic else NoOpHapticFeedback,
                    LocalHapticsEnabled provides hapticsEnabled.value,
                ) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "main",
                    enterTransition = {
                        slideIn(tween(340, easing = LinearOutSlowInEasing)) { IntOffset(it.width, 0) }
                    },
                    exitTransition = {
                        slideOut(tween(280, easing = FastOutLinearInEasing)) { IntOffset(-it.width / 3, 0) }
                    },
                    popEnterTransition = {
                        slideIn(tween(340, easing = LinearOutSlowInEasing)) { IntOffset(-it.width / 3, 0) }
                    },
                    popExitTransition = {
                        slideOut(tween(280, easing = FastOutLinearInEasing)) { IntOffset(it.width, 0) }
                    },
                ) {
                    composable("main") { MainScreen(navController) }
                    composable("settings") { SettingsScreen(navController) }
                    composable("settings/theme") { ThemeSettingsScreen(navController) }
                    composable("settings/notification") { NotificationSettingsScreen(navController) }
                    composable("settings/workarounds") { WorkaroundsSettingsScreen(navController) }
                }
                } // CompositionLocalProvider
            }
        }
    }
}
