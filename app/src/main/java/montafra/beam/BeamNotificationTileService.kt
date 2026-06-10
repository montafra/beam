package montafra.beam

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.concurrent.Executors

class BeamNotificationTileService : TileService() {

    // Persisting + notifying StatusService is done here so the click handler
    // never blocks the main thread on disk I/O.
    private val ioExecutor = Executors.newSingleThreadExecutor()

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
        val target = !prefs.getBoolean("notificationEnabled", true)

        // Reflect the new state on the tile immediately so the tap feels
        // instant; the slow work (commit() blocks on disk, service startup)
        // runs off the main thread below.
        qsTile?.let {
            it.state = if (target) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.updateTile()
        }

        if (target) {
            val needsPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPerm) {
                // Permission still required: open the app so it can request
                // POST_NOTIFICATIONS; MainActivity starts the service on grant
                // (and clears the flag again if the user denies it). Must run on
                // the main thread, and the activity launch hides the commit cost.
                prefs.edit().putBoolean("notificationEnabled", true).commit()
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
                return
            }
        }

        // Hot path (toggle off, or toggle on with permission already granted).
        // commit() (not apply()) before the broadcast/service start so the new
        // value is on disk for StatusService, which runs in a separate process
        // and reads prefs with MODE_MULTI_PROCESS.
        ioExecutor.execute {
            prefs.edit().putBoolean("notificationEnabled", target).commit()
            if (target) {
                startForegroundService(Intent(this, StatusService::class.java))
            } else {
                sendBroadcast(Intent(settingsUpdateInd).setPackage(packageName))
            }
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = getSharedPreferences(settingsName, MODE_PRIVATE)
            .getBoolean("notificationEnabled", true)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        // Ask the system to refresh the tile's cached state after
        // "notificationEnabled" is changed from elsewhere (e.g. the in-app
        // settings toggle) while the QS panel is closed. Triggers a one-shot
        // onStartListening() -> refreshTile(). No-op if the tile isn't added.
        fun requestRefresh(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, BeamNotificationTileService::class.java),
            )
        }
    }
}
