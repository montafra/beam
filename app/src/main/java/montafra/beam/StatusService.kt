package montafra.beam

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.Icon
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class StatusService : Service() {
    companion object {
        private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // How often an in-flight screen-on period is folded into the persisted total, so a
        // process kill loses at most this much instead of the whole period. It doubles as the
        // recovery bound: a live period is never older than this (plus one poll), so anything
        // beyond it is a gap the process slept through, not screen-on time.
        private const val screenTimeCheckpointMs = 60_000L
        // Tolerance when matching a persisted elapsedRealtime base against the current one.
        private const val screenTimeBootSlackMs = 5_000L
        // Below this much session time the on-time share is noise, so it is left out.
        private const val screenTimeSessionMinMs = 60_000L
        // Runtime state this service owns, deliberately kept out of the shared "settings" file.
        // SharedPreferences holds one in-memory map per process and rewrites the whole file on
        // every commit, so a settings write from the UI process restores every key it has not
        // seen since that process started — which would be all of these. Private to the companion
        // so nothing outside :batteryStatus can even name the file.
        private const val stateName = "service-state"
        // Weight of the notification icon text, matching Typeface.DEFAULT_BOLD. Fonts whose
        // wght axis stops lower are clamped to their own ceiling.
        private const val iconWeight = 700
        // Level alarms re-arm this many percent past their threshold, so boundary jitter
        // cannot fire them twice.
        private const val alarmLevelHysteresis = 2
    }

    /**
     * One alarm's configuration (from the shared settings file) and persisted runtime state.
     * Everything is keyed off [prefix], so the settings screen and this service cannot drift on
     * key names; thresholds default to the constants shared with AlarmsSettingsScreen.
     */
    private class Alarm(val prefix: String, val noteId: Int, val defaultThreshold: Int) {
        var enabled = false
        var threshold = defaultThreshold
        var repeat = false
        var fired = false
        var lastMs = 0L
    }

    private lateinit var battery: Battery
    /**
     * Screen-time and alarm bookkeeping, read and written only from :batteryStatus. MODE_PRIVATE
     * is correct for a single-process file: the in-memory map is the only copy, so there is
     * nothing to re-read from disk. Lazy because a Service field initialiser would run before the
     * base context is attached.
     */
    private val state: SharedPreferences by lazy { getSharedPreferences(stateName, MODE_PRIVATE) }
    private var iconBitmap: Bitmap? = null
    // The typeface follows the selected app font and is (re)applied in loadSettings();
    // DEFAULT_BOLD is both the initial value and the fallback for the system font.
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
    private val alarmLow = Alarm("alarmLow", alarmLowNoteId, alarmLowDefaultThreshold)
    private val alarmHigh = Alarm("alarmHigh", alarmHighNoteId, alarmHighDefaultThreshold)
    private val alarmTemp = Alarm("alarmTemp", alarmTempNoteId, alarmTempDefaultThreshold)
    private val alarms = listOf(alarmLow, alarmHigh, alarmTemp)
    private var alarmRepeatIntervalMs = alarmRepeatDefaultMin * 60_000L
    // Screen-time bookkeeping. Accumulated durations run off SystemClock.elapsedRealtime(),
    // which is monotonic and immune to NTP corrections and clock changes; only the session
    // baseline is wall clock, because it has to survive reboots. screenTimeOnStart is
    // therefore meaningful only within the boot that wrote it — see restoreServiceState().
    private var screenTimeSessionStart = 0L // wall clock, last unplug
    private var screenTimeOnTotal = 0L
    private var screenTimeOnStart = 0L      // elapsedRealtime, 0 when the screen is off
    private var screenTimeCheckpoint = 0L   // elapsedRealtime of the last flush to prefs
    // Last plug state observed, persisted so an unplug that happened while this process was dead
    // is still detectable. Null until a snapshot has told us, which is not the same as unplugged.
    private var wasPlugged: Boolean? = null
    private var stateRestored = false
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
                    if (alarms.any { it.enabled }) {
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
                    // Recorded before the reset so the persist inside it stores the edge, and so
                    // the syncPlugState() in update() below does not see the same edge a second
                    // time once the sticky battery broadcast catches up.
                    wasPlugged = false
                    startScreenTimeSession()
                    update()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    closeScreenOnPeriod()
                    persistScreenTime()
                    task.stop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    // start() ticks synchronously, and update() -> reconcileScreenTime() opens the
                    // period if the device is already past the keyguard. On a locked device that
                    // happens at ACTION_USER_PRESENT below, or at the first tick after the
                    // keyguard goes away.
                    task.start()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Only here to react without waiting for the next poll; reconcile decides, so
                    // a device that still reports the keyguard as locked at this point is picked
                    // up a tick later rather than credited from a screen that is not in use yet.
                    reconcileScreenTime()
                }
                // Android does not send ACTION_SCREEN_OFF on the way down, so without this the
                // open period would outlive the boot and restoreServiceState() would have to throw
                // it away wholesale. Closing it here keeps the pre-shutdown on-time. The write has
                // to block: apply() may not get to run before the process goes.
                Intent.ACTION_SHUTDOWN -> {
                    closeScreenOnPeriod()
                    persistScreenTime(sync = true)
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
        // The icon is a small ALPHA_8 bitmap, so it needs a bold weight to stay legible.
        // Pinning it matters: without it the variable fonts draw their default instance,
        // which is Light 300 for Space Grotesk.
        iconPaint.typeface = BeamFont.forKey(settings.getString("fontFamily", defaultFontKey))
            ?.typeface(this, iconWeight)
            ?: Typeface.DEFAULT_BOLD
        showTimeToFull = settings.getBoolean("showTimeToFull", true)
        showScreenTimeInNotification = settings.getBoolean("showScreenTimeInNotification", false)
        pollIntervalMs = settings.getLong("pollIntervalMs", intervalMs)
        alarms.forEach { a ->
            a.enabled = settings.getBoolean("${a.prefix}Enabled", false)
            a.threshold = settings.getInt("${a.prefix}Threshold", a.defaultThreshold)
            a.repeat = settings.getBoolean("${a.prefix}Repeat", false)
        }
        alarmRepeatIntervalMs = settings.getInt("alarmRepeatIntervalMin", alarmRepeatDefaultMin) * 60_000L
    }

    /**
     * Loads the state this service owns and repairs whatever the previous process left behind.
     *
     * Runs exactly once per process: this is recovery, not a reload. loadSettings() used to do it
     * on every settings update and every onStartCommand — and onStartCommand fires on every app
     * launch, every quick-settings tap and every START_STICKY redelivery — which threw the
     * in-memory total away in favour of the last checkpoint and closed a perfectly good open
     * period each time.
     *
     * Ordering: after init(), which creates the snapshot syncPlugState() reads, and after
     * loadSettings(), which supplies the pollIntervalMs used in the recovery bound below.
     */
    private fun restoreServiceState() {
        if (stateRestored) return
        stateRestored = true

        alarms.forEach { a ->
            a.fired = state.getBoolean("${a.prefix}Fired", false)
            a.lastMs = state.getLong("${a.prefix}LastMs", 0L)
        }

        screenTimeSessionStart = state.getLong("screenTimeSessionStart", 0L)
        screenTimeOnTotal = state.getLong("screenTimeOnTotal", 0L)
        screenTimeOnStart = state.getLong("screenTimeOnStart", 0L)
        // One-time migration: before this file existed, screen time lived in the shared settings
        // file. Session start (wall clock in both schemes) and the accumulated total carry over
        // as-is; the old open period ran on wall clock in some other boot and cannot be mapped
        // onto this boot's elapsedRealtime, so it is dropped (bounded loss: one open period).
        // The stale keys are read-only here — this process must not write the settings file (see
        // stateName above) — so they stay behind, harmlessly.
        if (!state.contains("screenTimeSessionStart")) {
            val legacy = getSharedPreferences(settingsName, MODE_MULTI_PROCESS)
            screenTimeSessionStart = legacy.getLong("screenTimeSessionStart", 0L)
            screenTimeOnTotal = legacy.getLong("screenTimeOnTotal", 0L)
        }
        screenTimeCheckpoint = screenTimeOnStart
        wasPlugged = when (state.getInt("screenTimePlugged", -1)) {
            1    -> true
            0    -> false
            else -> null // never observed, which is not the same as "not plugged"
        }

        // A persisted screenTimeOnStart is an elapsedRealtime value, so it only means anything
        // within the boot that wrote it. If the boot base moved (reboot, or a clock adjustment
        // large enough that we can no longer tell), or the value sits in the future, drop it:
        // crediting it would bill the entire powered-off gap as screen-on time.
        val storedBootBase = state.getLong("screenTimeBootBase", 0L)
        if (screenTimeOnStart > 0L &&
            (storedBootBase == 0L ||
                abs(bootBase() - storedBootBase) > screenTimeBootSlackMs ||
                screenTimeOnStart > SystemClock.elapsedRealtime())
        ) {
            debug("discarding screen-on period from another boot")
            screenTimeOnStart = 0L
            screenTimeCheckpoint = 0L
        }

        if (screenTimeSessionStart == 0L || screenTimeSessionStart > System.currentTimeMillis()) {
            screenTimeSessionStart = System.currentTimeMillis()
        }

        // Any period still open here was left by a process that is no longer running its poll
        // loop, so its age is only trustworthy up to one checkpoint plus a poll; beyond that the
        // screen may have gone off unobserved. Close it under that bound, then reopen from now if
        // the screen is currently on.
        closeScreenOnPeriod(screenTimeCheckpointMs + pollIntervalMs)
        if (screenInUse()) openScreenOnPeriod()
        persistScreenTime()

        // Last, because it may decide the session restored above is already over: an unplug that
        // happened while this process was dead leaves no broadcast behind, only a disagreement
        // between the stored plug state and the current one.
        syncPlugState()
    }

    /**
     * Whether the device is awake *and* past the keyguard, i.e. in use. Waking to the lock screen
     * without unlocking is not screen time, which is how the system counts it too.
     */
    private fun screenInUse(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
    }

    private fun bootBase() = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /** [sync] forces a blocking write, for the shutdown path where apply() may not get to run. */
    private fun persistScreenTime(sync: Boolean = false) {
        state.edit(commit = sync) {
            putLong("screenTimeSessionStart", screenTimeSessionStart)
            putLong("screenTimeOnTotal", screenTimeOnTotal)
            putLong("screenTimeOnStart", screenTimeOnStart)
            putLong("screenTimeBootBase", bootBase())
            // Tri-state: -1 is "never observed", which must not read back as "not plugged" or the
            // first snapshot after an install would look like an unplug.
            putInt("screenTimePlugged", when (wasPlugged) {
                true  -> 1
                false -> 0
                null  -> -1
            })
        }
    }

    /** Starts a fresh screen-time session: the counters run from this unplug onwards. */
    private fun startScreenTimeSession() {
        screenTimeSessionStart = System.currentTimeMillis()
        screenTimeOnTotal = 0L
        screenTimeOnStart = 0L
        screenTimeCheckpoint = 0L
        if (screenInUse()) openScreenOnPeriod()
        persistScreenTime()
    }

    /**
     * Detects the plugged -> unplugged edge and starts a new session on it.
     *
     * ACTION_POWER_DISCONNECTED only arrives while this process is alive with its receiver
     * registered, and there is no manifest receiver, so on its own it drops every unplug that
     * happens between service deaths — after which screenTimeOnTotal just keeps growing across
     * charge cycles. Comparing the persisted plug state against the snapshot catches the edge
     * regardless of who was running at the time, reboots included.
     *
     * This reads EXTRA_PLUGGED rather than snapshot.charging on purpose: charging is false for
     * BATTERY_STATUS_NOT_CHARGING, which is what devices with charge limiting report while still
     * plugged in, and resetting the session mid-charge is exactly the bug being fixed.
     */
    private fun syncPlugState() {
        val plugged = snapshot.plugged ?: return // no plug state in the broadcast: not an edge
        if (plugged == wasPlugged) return
        val unplugged = wasPlugged == true && !plugged
        wasPlugged = plugged
        if (unplugged) startScreenTimeSession() else persistScreenTime()
    }

    /**
     * Folds the in-flight screen-on period into the total and marks the screen as off.
     * No-op when no period is open. [maxSliceMs] caps how much of the period is credited.
     */
    private fun closeScreenOnPeriod(maxSliceMs: Long = Long.MAX_VALUE) {
        if (screenTimeOnStart == 0L) return
        screenTimeOnTotal +=
            (SystemClock.elapsedRealtime() - screenTimeOnStart).coerceIn(0L, maxSliceMs)
        screenTimeOnStart = 0L
        screenTimeCheckpoint = 0L
    }

    /** Starts a screen-on period, leaving an already-running one alone. */
    private fun openScreenOnPeriod() {
        if (screenTimeOnStart > 0L) return
        screenTimeOnStart = SystemClock.elapsedRealtime()
        screenTimeCheckpoint = screenTimeOnStart
    }

    /**
     * Brings the open/closed state of the screen-on period back in line with reality, then rebases
     * an open period onto the persisted total every [screenTimeCheckpointMs].
     *
     * The broadcasts are only hints. ACTION_USER_PRESENT can arrive late, the keyguard can
     * re-lock while the display stays on (the keepScreenOn option), the shade can be pulled down
     * over the lock screen, and anything sent before this service started was missed outright —
     * the screen broadcasts cannot be declared in the manifest, so there is no way to catch those.
     * Deriving the state from screenInUse() on every tick makes a lost edge cost one poll interval
     * instead of a whole session.
     */
    private fun reconcileScreenTime() {
        val inUse = screenInUse()
        if (inUse && screenTimeOnStart == 0L) {
            openScreenOnPeriod()
            persistScreenTime()
            return
        }
        if (!inUse && screenTimeOnStart > 0L) {
            closeScreenOnPeriod()
            persistScreenTime()
            return
        }
        if (screenTimeOnStart == 0L) return

        // Steady state: fold the in-flight period into the total now and then, so a process kill
        // loses at most one checkpoint interval and recovery has a bound it can trust.
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - screenTimeCheckpoint < screenTimeCheckpointMs) return
        screenTimeOnTotal += (elapsed - screenTimeOnStart).coerceAtLeast(0L)
        screenTimeOnStart = elapsed
        screenTimeCheckpoint = elapsed
        persistScreenTime()
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
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SHUTDOWN)
            },
            RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCreate() {
        super.onCreate()
        init()
        loadSettings()
        restoreServiceState()
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
                Log.e(this::class.java.name, "Failed to foreground StatusService", e)
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
            // ARGB_8888, not ALPHA_8: Notification.build() copies and scales the icon, and
            // some ROMs return null from Bitmap.copy for ALPHA_8 (NPE in Icon.setBitmap).
            Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888).also { iconBitmap = it }
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

        // Hand over an immutable snapshot: the reused bitmap is erased on the next tick.
        return Icon.createWithBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap)
    }

    private fun buildNotification(): Notification {
        val iconValue = metricValue(notificationIndicator)
        val iconUnit  = metricUnit(notificationIndicator)
        val timeText = if (!showTimeToFull) "" else when (val seconds = snapshot.secondsUntilCharged) {
            null -> ""
            0.0  -> getString(R.string.fullyCharged)
            else -> getString(R.string.untilFullCharge, fmtSeconds(seconds))
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

        return try {
            builder.build()
        } catch (e: RuntimeException) {
            // Some ROMs still reject the rendered bitmap icon while copying/scaling it;
            // fall back to a static icon rather than kill the service.
            Log.w(this::class.java.name, "Rendered notification icon rejected", e)
            builder.setSmallIcon(R.drawable.ico_lightning).build()
        }
    }

    private fun screenTimeFormatted(): String {
        val onMs = screenTimeOnTotal + if (screenTimeOnStart > 0L) {
            (SystemClock.elapsedRealtime() - screenTimeOnStart).coerceAtLeast(0L)
        } else 0L
        val onText = fmtDurationHms(onMs / 1000)

        // Wall clock, since the session spans reboots and time the device spent powered off.
        // Just after an unplug the denominator is too small for the share to mean anything, and a
        // clock that moved backwards makes it negative; report the duration alone in both cases
        // rather than a bogus percentage.
        val sessionMs = System.currentTimeMillis() - screenTimeSessionStart
        if (sessionMs < screenTimeSessionMinMs) return onText

        val percent = (onMs * 100.0 / sessionMs).roundToLong().coerceIn(0L, 100L)
        return getString(R.string.screenTimeFormat, onText, percent.toInt())
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
        // Order matters: a session reset that ran after reconcile would zero the period reconcile
        // had just opened and leave it closed until the next tick.
        syncPlugState()
        reconcileScreenTime()
        if (notificationEnabled) noteMgr.notify(noteId, buildNotification())
        checkAlarms()
        updateData()
    }

    private fun checkAlarms() {
        // Cheap when alarms are disabled (runAlarm just keeps their state cleared); this also
        // lets the update()/settings-update path reset state when an alarm is turned off.
        val now = System.currentTimeMillis()
        // Plug state, not charging status: BATTERY_STATUS_NOT_CHARGING is what charge-limiting
        // and adaptive-charging devices report while still on the cable (see syncPlugState()),
        // and treating those pauses as "unplugged" would re-arm the high alarm on every hold
        // cycle and fire the low alarm mid-charge. charging is the fallback for the rare
        // broadcast without EXTRA_PLUGGED.
        val plugged = snapshot.plugged ?: snapshot.charging
        var changed = false

        snapshot.levelPercent?.roundToInt()?.let { level ->
            // Level alarms re-arm a band past their threshold (mirroring the temperature
            // band below), so 1% jitter at the boundary cannot re-fire them.
            if (runAlarm(
                    alarmLow, now,
                    active = !plugged && level <= alarmLow.threshold,
                    rearmed = plugged || level > alarmLow.threshold + alarmLevelHysteresis,
                ) {
                    buildAlarmNotification(
                        getString(R.string.alarmLowTitle),
                        getString(R.string.alarmLowText, level),
                    )
                }
            ) changed = true

            if (runAlarm(
                    alarmHigh, now,
                    active = plugged && level >= alarmHigh.threshold,
                    rearmed = !plugged || level <= alarmHigh.threshold - alarmLevelHysteresis,
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
                    alarmTemp, now,
                    active = temp >= alarmTemp.threshold,
                    rearmed = temp <= alarmTemp.threshold - 2,
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
     * Runs one alarm's state machine. Fires (or re-fires, when the alarm's repeat is set) the
     * notification built by [build] on transition into the alarm condition, and re-arms once the
     * value has recovered ([rearmed]). Returns true if the persisted runtime state changed.
     */
    private fun runAlarm(
        alarm: Alarm,
        now: Long,
        active: Boolean,
        rearmed: Boolean,
        build: () -> Notification,
    ): Boolean {
        val prevFired = alarm.fired
        val prevLast = alarm.lastMs
        if (!alarm.enabled) {
            alarm.fired = false
            alarm.lastMs = 0L
        } else {
            if (rearmed) {
                alarm.fired = false
                alarm.lastMs = 0L
            }
            if (active) {
                val shouldFire = !alarm.fired ||
                    (alarm.repeat && now - alarm.lastMs >= alarmRepeatIntervalMs)
                // Fired is only recorded when the notification can actually reach the user.
                // With the permission or the alarms channel blocked, notify() is silently
                // dropped, and marking the alarm fired anyway would swallow the alert for good;
                // left armed, it goes off as soon as notifications come back while the
                // condition still holds.
                if (shouldFire && canPostAlarms()) {
                    noteMgr.notify(alarm.noteId, build())
                    alarm.fired = true
                    alarm.lastMs = now
                }
            }
        }
        return alarm.fired != prevFired || alarm.lastMs != prevLast
    }

    private fun canPostAlarms(): Boolean =
        noteMgr.areNotificationsEnabled() &&
            noteMgr.getNotificationChannel(alarmChannelId)?.importance !=
                NotificationManager.IMPORTANCE_NONE

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
        state.edit {
            alarms.forEach { a ->
                putBoolean("${a.prefix}Fired", a.fired)
                putLong("${a.prefix}LastMs", a.lastMs)
            }
        }
    }
}
