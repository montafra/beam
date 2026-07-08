package montafra.beam

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.Icon
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.core.content.edit
import kotlin.math.roundToInt


class StatusService : Service() {
    companion object {
        private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    private class AlarmRuntime {
        var fired = false
        var lastMs = 0L
    }

    private lateinit var battery: Battery
    private var iconBitmap: Bitmap? = null
    private val iconPaint = Paint().apply {
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private var indicatorEntries: Set<String> = emptySet()
    private var notificationIndicator: String = "W"
    private var showTimeToFull: Boolean = true
    private var showScreenTimeInNotification: Boolean = false
    private var pollIntervalMs: Long = intervalMs
    private var alarmLowEnabled = false
    private var alarmLowThreshold = 20
    private var alarmLowRepeat = false
    private var alarmHighEnabled = false
    private var alarmHighThreshold = 85
    private var alarmHighRepeat = false
    private var alarmTempEnabled = false
    private var alarmTempThreshold = 40
    private var alarmTempRepeat = false
    private var alarmRepeatIntervalMs = 15 * 60_000L
    private val alarmLowState = AlarmRuntime()
    private val alarmHighState = AlarmRuntime()
    private val alarmTempState = AlarmRuntime()
    private var screenTimeSessionStart = 0L
    private var screenTimeOnTotal = 0L
    private var screenTimeOnStart = 0L
    private var notificationEnabled = true
    private var useFahrenheit = false
    private var initialized = false
    private lateinit var msgReceiver: MsgReceiver
    private val metricOrder = listOf("W", "A", "Ah", "C", "V", "Wh", "%")
    private lateinit var noteIntent: PendingIntent
    private lateinit var noteMgr: NotificationManager
    private var pluggedInAt: ZonedDateTime? = null
    private lateinit var snapshot: BatterySnapshot
    private val task = PeriodicTask({ update() }, { pollIntervalMs })
    private val binder = Binder()

    private fun debug(msg: String) {
        Log.d(this::class.java.name, msg)
    }

    private inner class MsgReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                batteryDataReq -> updateData()
                settingsUpdateInd -> {
                    loadSettings()
                    update()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    // Driven by the OS even while the screen is off (the PeriodicTask is
                    // paused then), so alarms still fire. Only refresh the snapshot + check
                    // thresholds here; the status notification is left untouched. Skipped
                    // entirely when no alarm is enabled, so non-users don't wake on every change.
                    if (alarmLowEnabled || alarmHighEnabled || alarmTempEnabled) {
                        snapshot = battery.snapshot()
                        checkAlarms()
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    pluggedInAt = ZonedDateTime.now()
                    update()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    pluggedInAt = null
                    val now = System.currentTimeMillis()
                    screenTimeSessionStart = now
                    screenTimeOnTotal = 0L
                    screenTimeOnStart = if ((getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive) now else 0L
                    getSharedPreferences(settingsName, MODE_MULTI_PROCESS).edit {
                        putLong("screenTimeSessionStart", screenTimeSessionStart)
                            .putLong("screenTimeOnTotal", screenTimeOnTotal)
                            .putLong("screenTimeOnStart", screenTimeOnStart)
                    }
                    update()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (screenTimeOnStart > 0L) {
                        screenTimeOnTotal += System.currentTimeMillis() - screenTimeOnStart
                        screenTimeOnStart = 0L
                        getSharedPreferences(settingsName, MODE_MULTI_PROCESS).edit {
                            putLong("screenTimeOnTotal", screenTimeOnTotal)
                            putLong("screenTimeOnStart", screenTimeOnStart)
                        }
                    }
                    task.stop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenTimeOnStart = System.currentTimeMillis()
                    getSharedPreferences(settingsName, MODE_MULTI_PROCESS).edit {
                        putLong("screenTimeOnStart", screenTimeOnStart)
                    }
                    task.start()
                }
            }
        }
    }

    private fun loadSettings() {
        val settings = getSharedPreferences(settingsName, MODE_MULTI_PROCESS)
        notificationEnabled = settings.getBoolean("notificationEnabled", true)
        if (!notificationEnabled) stopForeground(STOP_FOREGROUND_REMOVE)
        battery.currentScalar = settings.getFloat("currentScalar", 1f).toDouble()
        battery.invertCurrent = settings.getBoolean("invertCurrent", false)
        useFahrenheit = settings.getBoolean("useFahrenheit", false)
        indicatorEntries = settings.getStringSet("indicatorEntries", null) ?: emptySet()
        notificationIndicator = settings.getString("notificationIndicator", "W") ?: "W"
        showTimeToFull = settings.getBoolean("showTimeToFull", true)
        showScreenTimeInNotification = settings.getBoolean("showScreenTimeInNotification", false)
        pollIntervalMs = settings.getLong("pollIntervalMs", intervalMs)
        alarmLowEnabled = settings.getBoolean("alarmLowEnabled", false)
        alarmLowThreshold = settings.getInt("alarmLowThreshold", 20)
        alarmLowRepeat = settings.getBoolean("alarmLowRepeat", false)
        alarmHighEnabled = settings.getBoolean("alarmHighEnabled", false)
        alarmHighThreshold = settings.getInt("alarmHighThreshold", 85)
        alarmHighRepeat = settings.getBoolean("alarmHighRepeat", false)
        alarmTempEnabled = settings.getBoolean("alarmTempEnabled", false)
        alarmTempThreshold = settings.getInt("alarmTempThreshold", 40)
        alarmTempRepeat = settings.getBoolean("alarmTempRepeat", false)
        alarmRepeatIntervalMs = settings.getInt("alarmRepeatIntervalMin", 15) * 60_000L
        alarmLowState.fired = settings.getBoolean("alarmLowFired", false)
        alarmLowState.lastMs = settings.getLong("alarmLowLastMs", 0L)
        alarmHighState.fired = settings.getBoolean("alarmHighFired", false)
        alarmHighState.lastMs = settings.getLong("alarmHighLastMs", 0L)
        alarmTempState.fired = settings.getBoolean("alarmTempFired", false)
        alarmTempState.lastMs = settings.getLong("alarmTempLastMs", 0L)
        screenTimeSessionStart = settings.getLong("screenTimeSessionStart", 0L)
        screenTimeOnTotal = settings.getLong("screenTimeOnTotal", 0L)
        screenTimeOnStart = settings.getLong("screenTimeOnStart", 0L)
        if (screenTimeSessionStart == 0L) {
            screenTimeSessionStart = System.currentTimeMillis()
            settings.edit { putLong("screenTimeSessionStart", screenTimeSessionStart) }
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            if (screenTimeOnStart == 0L) screenTimeOnStart = System.currentTimeMillis()
        } else {
            if (screenTimeOnStart > 0L) {
                screenTimeOnTotal += System.currentTimeMillis() - screenTimeOnStart
                screenTimeOnStart = 0L
                settings.edit {
                    putLong("screenTimeOnTotal", screenTimeOnTotal)
                    putLong("screenTimeOnStart", screenTimeOnStart)
                }
            }
        }
    }

    private fun metricLabel(key: String) = getString(when (key) {
        "A"        -> R.string.current
        "Ah", "Wh" -> R.string.energy
        "C"        -> R.string.temperature
        "V"        -> R.string.voltage
        "%"        -> R.string.chargeLevel
        else       -> R.string.power
    })

    // Temperature is stored/derived in Celsius; convert to the user's chosen unit at display time.
    private fun displayTemp(celsius: Double?) =
        if (useFahrenheit) celsius?.let { cToF(it) } else celsius

    private fun tempUnit() = if (useFahrenheit) "°F" else "°C"

    private fun metricValue(key: String) = when (key) {
        "%"  -> fmtPercent(snapshot.levelPercent)
        else -> fmt(when (key) {
            "A"  -> snapshot.amps
            "Ah" -> snapshot.energyAmpHours
            "C"  -> displayTemp(snapshot.celsius)
            "V"  -> snapshot.volts
            "Wh" -> snapshot.energyWattHours
            else -> snapshot.watts
        })
    }

    private fun metricUnit(key: String) = if (key == "C") tempUnit() else key

    private fun init() {
        if (initialized) return
        initialized = true

        battery = Battery(applicationContext)
        snapshot = battery.snapshot()

        noteMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        noteMgr.createNotificationChannel(
            NotificationChannel(
                noteChannelId,
                "Power Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Continuously displays current battery power consumption"
            }
        )
        noteMgr.createNotificationChannel(
            NotificationChannel(
                alarmChannelId,
                getString(R.string.alarmChannelName),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.alarmChannelDesc)
            }
        )

        noteIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        msgReceiver = MsgReceiver()
        registerReceiver(
            msgReceiver,
            IntentFilter().apply {
                addAction(batteryDataReq)
                addAction(settingsUpdateInd)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCreate() {
        super.onCreate()
        init()
        loadSettings()
        task.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        debug("onStartCommand()")

        super.onStartCommand(intent, flags, startId)

        loadSettings()

        if (notificationEnabled) {
            try {
                startForeground(noteId, buildNotification())
            } catch (e: Exception) {
                error("Failed to foreground StatusService: ${e.message}")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        debug("onDestroy()")
        task.stop()
        if (initialized) unregisterReceiver(msgReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun renderIcon(value: String, unit: String): Icon {
        val density = resources.displayMetrics.density
        val w = (48f * density).toInt()
        val bitmap = iconBitmap?.takeIf { it.width == w } ?: run {
            Bitmap.createBitmap(w, w, Bitmap.Config.ALPHA_8).also { iconBitmap = it }
        }
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)

        val maxWidth = w * 0.92f
        iconPaint.textSize = 40f * density
        val measured = iconPaint.measureText(value)
        if (measured > maxWidth) iconPaint.textSize *= maxWidth / measured
        canvas.drawText(value, w / 2f, w * 0.62f, iconPaint)

        iconPaint.textSize = 18f * density
        canvas.drawText(unit, w / 2f, w * 0.94f, iconPaint)

        return Icon.createWithBitmap(bitmap)
    }

    private fun buildNotification(): Notification {
        val iconValue = metricValue(notificationIndicator)
        val iconUnit  = metricUnit(notificationIndicator)
        val timeText = if (!showTimeToFull) "" else when (val seconds = snapshot.secondsUntilCharged) {
            null -> ""
            0.0  -> getString(R.string.fullyCharged)
            else -> "${fmtSeconds(seconds)} until full charge"
        }

        val builder = Notification.Builder(this, noteChannelId)
            .setContentTitle("$iconValue $iconUnit")
            .setSmallIcon(renderIcon(iconValue, iconUnit))
            .setContentIntent(noteIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val entries = indicatorEntries.filter { it != notificationIndicator }.sortedBy { metricOrder.indexOf(it) }
        val screenTimeText = if (showScreenTimeInNotification) screenTimeFormatted() else null

        if (entries.isNotEmpty() || screenTimeText != null) {
            val style = Notification.InboxStyle()
            entries.forEach { key ->
                style.addLine("${metricLabel(key)}  ${metricValue(key)}${metricUnit(key)}")
            }
            if (screenTimeText != null) {
                style.addLine("${getString(R.string.screenTime)}  $screenTimeText")
            }
            if (timeText.isNotEmpty()) style.setSummaryText(timeText)
            val compactParts = mutableListOf<String>()
            entries.forEach { k -> compactParts.add("${metricValue(k)}${metricUnit(k)}") }
            if (screenTimeText != null) compactParts.add(screenTimeText)
            builder.setStyle(style).setContentText(compactParts.joinToString("  "))
        } else {
            builder.setStyle(null).setContentText(timeText)
        }

        return builder.build()
    }

    private fun screenTimeFormatted(): String {
        val now = System.currentTimeMillis()
        val currentOnMs = screenTimeOnTotal +
            (if (screenTimeOnStart > 0L) now - screenTimeOnStart else 0L)
        val totalElapsedMs = (now - screenTimeSessionStart).coerceAtLeast(1)
        val percent = ((currentOnMs * 100) / totalElapsedMs).coerceIn(0, 100)
        return getString(R.string.screenTimeFormat, fmtDurationHms(currentOnMs / 1000), percent)
    }

    private fun updateData() {
        val plugType = snapshot.plugType?.name?.lowercase()
        val indeterminate = getString(R.string.indeterminate)
        val fullyCharged = getString(R.string.fullyCharged)
        val no = getString(R.string.no)
        val yes = getString(R.string.yes)

        val intent = Intent()
            .setPackage(packageName)
            .setAction(batteryDataResp)
            .putExtra("charging",
                when (snapshot.charging) {
                    true -> if (plugType == null) yes else "$yes ($plugType)"
                    false -> no
                }
            )
            .putExtra("chargeLevel", fmtPercent(snapshot.levelPercent) + "%")
            .putExtra("chargingSince",
                when (val pluggedInAt = pluggedInAt) {
                    null -> indeterminate
                    else -> LocalDateTime
                        .ofInstant(pluggedInAt.toInstant(), pluggedInAt.zone)
                        .format(dateFmt)
                }
            )
            .putExtra("current", fmt(snapshot.amps) + "A")
            .putExtra("energy",
                "${fmt(snapshot.energyWattHours)}Wh (${fmt(snapshot.energyAmpHours)}Ah)"
            )
            .putExtra("power", fmt(snapshot.watts) + "W")
            .putExtra("temperature", fmt(displayTemp(snapshot.celsius)) + tempUnit())
            .putExtra("timeToFullCharge",
                when (val seconds = snapshot.secondsUntilCharged) {
                    null -> indeterminate
                    0.0 -> fullyCharged
                    else -> fmtSeconds(seconds)
                }
            )
            .putExtra("voltage", fmt(snapshot.volts) + "V")
            .putExtra("screenTime", screenTimeFormatted())

        applicationContext.sendBroadcast(intent)
    }

    private fun update() {
        debug("update()")

        snapshot = battery.snapshot()
        if (notificationEnabled) noteMgr.notify(noteId, buildNotification())
        checkAlarms()
        updateData()
    }

    private fun checkAlarms() {
        // Cheap when alarms are disabled (runAlarm just keeps their state cleared); this also
        // lets the update()/settings-update path reset state when an alarm is turned off.
        val now = System.currentTimeMillis()
        val charging = snapshot.charging
        var changed = false

        snapshot.levelPercent?.roundToInt()?.let { level ->
            if (runAlarm(
                    alarmLowState, alarmLowEnabled,
                    active = !charging && level <= alarmLowThreshold,
                    rearmed = charging || level > alarmLowThreshold,
                    repeat = alarmLowRepeat, noteId = alarmLowNoteId, now = now,
                ) {
                    buildAlarmNotification(
                        getString(R.string.alarmLowTitle),
                        getString(R.string.alarmLowText, level),
                    )
                }
            ) changed = true

            if (runAlarm(
                    alarmHighState, alarmHighEnabled,
                    active = charging && level >= alarmHighThreshold,
                    rearmed = !charging || level < alarmHighThreshold,
                    repeat = alarmHighRepeat, noteId = alarmHighNoteId, now = now,
                ) {
                    buildAlarmNotification(
                        getString(R.string.alarmHighTitle),
                        getString(R.string.alarmHighText, level),
                    )
                }
            ) changed = true
        }

        snapshot.celsius?.let { temp ->
            if (runAlarm(
                    alarmTempState, alarmTempEnabled,
                    active = temp >= alarmTempThreshold,
                    rearmed = temp <= alarmTempThreshold - 2,
                    repeat = alarmTempRepeat, noteId = alarmTempNoteId, now = now,
                ) {
                    buildAlarmNotification(
                        getString(R.string.alarmTempTitle),
                        getString(
                            if (useFahrenheit) R.string.alarmTempTextF else R.string.alarmTempText,
                            (if (useFahrenheit) cToF(temp) else temp).roundToInt(),
                        ),
                    )
                }
            ) changed = true
        }

        if (changed) persistAlarmState()
    }

    /**
     * Runs one alarm's state machine. Fires (or re-fires, when [repeat] is set) the notification
     * built by [build] on transition into the alarm condition, and re-arms once the value has
     * recovered ([rearmed]). Returns true if the persisted runtime state changed.
     */
    private fun runAlarm(
        state: AlarmRuntime,
        enabled: Boolean,
        active: Boolean,
        rearmed: Boolean,
        repeat: Boolean,
        noteId: Int,
        now: Long,
        build: () -> Notification,
    ): Boolean {
        val prevFired = state.fired
        val prevLast = state.lastMs
        if (!enabled) {
            state.fired = false
            state.lastMs = 0L
        } else {
            if (rearmed) {
                state.fired = false
                state.lastMs = 0L
            }
            if (active) {
                val shouldFire = !state.fired ||
                    (repeat && now - state.lastMs >= alarmRepeatIntervalMs)
                if (shouldFire) {
                    noteMgr.notify(noteId, build())
                    state.fired = true
                    state.lastMs = now
                }
            }
        }
        return state.fired != prevFired || state.lastMs != prevLast
    }

    private fun buildAlarmNotification(title: String, text: String): Notification =
        Notification.Builder(this, alarmChannelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ico_alarm)
            .setContentIntent(noteIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()

    private fun persistAlarmState() {
        getSharedPreferences(settingsName, MODE_MULTI_PROCESS).edit {
            putBoolean("alarmLowFired", alarmLowState.fired)
            putLong("alarmLowLastMs", alarmLowState.lastMs)
            putBoolean("alarmHighFired", alarmHighState.fired)
            putLong("alarmHighLastMs", alarmHighState.lastMs)
            putBoolean("alarmTempFired", alarmTempState.fired)
            putLong("alarmTempLastMs", alarmTempState.lastMs)
        }
    }
}
