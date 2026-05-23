package montafra.beam

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class BeamTempWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val CACHE_KEY = "widgetTempCache"
        private const val PLACEHOLDER = "-"

        private fun buildViews(context: Context, temperature: String): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_temp)
            views.setTextViewText(R.id.widget_temp_value, temperature)
            views.setOnClickPendingIntent(
                R.id.widget_temp_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }

        private fun widgetIds(context: Context, mgr: AppWidgetManager): IntArray =
            mgr.getAppWidgetIds(ComponentName(context, BeamTempWidgetProvider::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != batteryDataResp) return
        val temperature = intent.getStringExtra("temperature") ?: return
        val mgr = AppWidgetManager.getInstance(context)
        val ids = widgetIds(context, mgr)
        if (ids.isEmpty()) return
        context.getSharedPreferences(settingsName, Context.MODE_MULTI_PROCESS)
            .edit().putString(CACHE_KEY, temperature).apply()
        val views = buildViews(context, temperature)
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val cached = context.getSharedPreferences(settingsName, Context.MODE_MULTI_PROCESS)
            .getString(CACHE_KEY, PLACEHOLDER) ?: PLACEHOLDER
        val views = buildViews(context, cached)
        for (id in ids) mgr.updateAppWidget(id, views)
        context.sendBroadcast(Intent(batteryDataReq).setPackage(context.packageName))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val prefs = context.getSharedPreferences(settingsName, Context.MODE_MULTI_PROCESS)
        if (prefs.getBoolean("notificationEnabled", true)) {
            try {
                context.startForegroundService(Intent(context, StatusService::class.java))
            } catch (_: Exception) {
            }
        }
        context.sendBroadcast(Intent(batteryDataReq).setPackage(context.packageName))
    }
}
