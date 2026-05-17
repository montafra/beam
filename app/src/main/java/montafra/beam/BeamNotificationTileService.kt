package montafra.beam

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BeamNotificationTileService : TileService() {

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "notificationEnabled") refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        getSharedPreferences(settingsName, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefListener)
        refreshTile()
    }

    override fun onStopListening() {
        getSharedPreferences(settingsName, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(settingsName, MODE_PRIVATE)
        val enabled = prefs.getBoolean("notificationEnabled", true)
        if (enabled) {
            prefs.edit().putBoolean("notificationEnabled", false).commit()
            sendBroadcast(Intent(settingsUpdateInd).setPackage(packageName))
        } else {
            // Enable the persistent notification immediately. commit() (not
            // apply()) so the value is visible to StatusService, which runs in
            // a separate process and reads prefs with MODE_MULTI_PROCESS.
            prefs.edit().putBoolean("notificationEnabled", true).commit()
            val needsPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPerm) {
                // Permission still required: open the app so it can request
                // POST_NOTIFICATIONS; MainActivity starts the service on grant
                // (and clears the flag again if the user denies it).
                val launchIntent = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pi = PendingIntent.getActivity(
                        this, 0, launchIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launchIntent)
                }
            } else {
                startForegroundService(Intent(this, StatusService::class.java))
            }
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = getSharedPreferences(settingsName, MODE_PRIVATE)
            .getBoolean("notificationEnabled", true)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
