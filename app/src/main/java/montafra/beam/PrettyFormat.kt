package montafra.beam

import java.util.Locale
import kotlin.math.absoluteValue

fun fmt(v: Double?): String {
    if (v == null)
        return "- "

    if (v.absoluteValue >= 10.0)
        return "%.0f".format(v)

    return "%.1f".format(Locale.ROOT, v)
}

fun fmtPercent(v: Double?): String {
    if (v == null)
        return "- "

    return "%.0f".format(v)
}

fun fmtDurationHms(seconds: Long): String {
    var secs = seconds.coerceAtLeast(0)
    val hours = secs / 3600
    val minutes = (secs % 3600) / 60
    secs %= 60
    return "%02d:%02d:%02d".format(hours, minutes, secs)
}

fun fmtSeconds(seconds: Double?): String {
    if (seconds == null)
        return ""

    var secs = seconds.toInt()
    if (secs < 60)
        return "${secs}s"

    var mins = secs / 60
    secs %= 60
    if (mins < 60)
        return "${mins}m ${secs}s"

    val hrs = mins / 60
    mins %= 60
    return "${hrs}h ${mins}m ${secs}s"
}
