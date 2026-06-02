package montafra.beam

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil

class BeamTempWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val CACHE_KEY = "widgetTempCache"
        private const val PLACEHOLDER = "-"
        private const val VALUE_COLOR = 0xFFFFFFFF.toInt()
        private const val LABEL_COLOR = 0x99FFFFFF.toInt()

        // The selectable app fonts (res/font) keyed the same way as the Compose theme
        // (see ui/theme/Type.kt#fontFamilyFor). "default" and unknown keys → null (system font).
        private fun fontResFor(key: String?): Int? = when (key) {
            "inter" -> R.font.inter
            "dm_sans" -> R.font.dm_sans
            "space_grotesk" -> R.font.space_grotesk
            "jetbrains_mono" -> R.font.jetbrains_mono
            "noto_sans_mono" -> R.font.noto_sans_mono
            else -> null
        }

        // RemoteViews can't apply a bundled res/font typeface to a TextView before API 31,
        // so for a custom font we draw the text to a bitmap (same approach as the
        // notification icon in StatusService) and show it via an ImageView instead.
        private fun renderText(context: Context, text: String, spSize: Float, color: Int, typeface: Typeface): Bitmap {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.typeface = typeface
                this.style = Paint.Style.FILL
                textSize = spSize * context.resources.displayMetrics.scaledDensity
            }
            val fm = paint.fontMetrics
            val width = ceil(paint.measureText(text)).toInt().coerceAtLeast(1)
            val height = ceil(fm.bottom - fm.top).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawText(text, 0f, -fm.top, paint)
            return bitmap
        }

        private fun buildViews(context: Context, temperature: String): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_temp)

            val fontKey = context.getSharedPreferences(settingsName, Context.MODE_MULTI_PROCESS)
                .getString("fontFamily", "default")
            val typeface = fontResFor(fontKey)?.let {
                try { ResourcesCompat.getFont(context, it) } catch (_: Exception) { null }
            }

            if (typeface == null) {
                // Default font: keep the native, scalable TextViews.
                views.setTextViewText(R.id.widget_temp_value, temperature)
                views.setViewVisibility(R.id.widget_temp_value, View.VISIBLE)
                views.setViewVisibility(R.id.widget_temp_label, View.VISIBLE)
                views.setViewVisibility(R.id.widget_temp_value_img, View.GONE)
                views.setViewVisibility(R.id.widget_temp_label_img, View.GONE)
            } else {
                // Custom app font: render the value and label as bitmaps.
                views.setImageViewBitmap(
                    R.id.widget_temp_value_img,
                    renderText(context, temperature, 32f, VALUE_COLOR, typeface),
                )
                views.setImageViewBitmap(
                    R.id.widget_temp_label_img,
                    renderText(context, context.getString(R.string.temperature), 11f, LABEL_COLOR, typeface),
                )
                views.setViewVisibility(R.id.widget_temp_value_img, View.VISIBLE)
                views.setViewVisibility(R.id.widget_temp_label_img, View.VISIBLE)
                views.setViewVisibility(R.id.widget_temp_value, View.GONE)
                views.setViewVisibility(R.id.widget_temp_label, View.GONE)
            }

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

        /** Force every placed widget to redraw — used when the app font changes. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = widgetIds(context, mgr)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, BeamTempWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
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
