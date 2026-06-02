package montafra.beam.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import montafra.beam.settingsName

data class ThemePrefs(val mode: String = "system", val customColor: Int? = null, val fontFamily: String = "default", val outlineOnlyCards: Boolean = false)

private fun hsvColor(hue: Float, sat: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

private fun seedColorScheme(seed: Color, isDark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val h = hsv[0]
    return if (!isDark) lightColorScheme(
        primary                 = hsvColor(h, 0.75f, 0.52f),
        onPrimary               = Color.White,
        primaryContainer        = hsvColor(h, 0.22f, 0.97f),
        onPrimaryContainer      = hsvColor(h, 0.88f, 0.22f),
        secondary               = hsvColor(h, 0.38f, 0.52f),
        onSecondary             = Color.White,
        secondaryContainer      = hsvColor(h, 0.18f, 0.93f),
        onSecondaryContainer    = hsvColor(h, 0.72f, 0.24f),
        surface                 = hsvColor(h, 0.04f, 0.98f),
        onSurface               = hsvColor(h, 0.08f, 0.11f),
        surfaceVariant          = hsvColor(h, 0.11f, 0.91f),
        onSurfaceVariant        = hsvColor(h, 0.20f, 0.37f),
        surfaceContainerLowest  = hsvColor(h, 0.02f, 1.00f),
        surfaceContainerLow     = hsvColor(h, 0.03f, 0.97f),
        surfaceContainer        = hsvColor(h, 0.05f, 0.94f),
        surfaceContainerHigh    = hsvColor(h, 0.07f, 0.91f),
        surfaceContainerHighest = hsvColor(h, 0.10f, 0.88f),
        background              = hsvColor(h, 0.04f, 0.98f),
        onBackground            = hsvColor(h, 0.08f, 0.11f),
        outline                 = hsvColor(h, 0.22f, 0.55f),
        outlineVariant          = hsvColor(h, 0.12f, 0.80f),
    ) else darkColorScheme(
        primary                 = hsvColor(h, 0.48f, 0.93f),
        onPrimary               = hsvColor(h, 0.88f, 0.20f),
        primaryContainer        = hsvColor(h, 0.82f, 0.34f),
        onPrimaryContainer      = hsvColor(h, 0.22f, 0.96f),
        secondary               = hsvColor(h, 0.32f, 0.82f),
        onSecondary             = hsvColor(h, 0.80f, 0.19f),
        secondaryContainer      = hsvColor(h, 0.42f, 0.28f),
        onSecondaryContainer    = hsvColor(h, 0.18f, 0.92f),
        surface                 = hsvColor(h, 0.07f, 0.10f),
        onSurface               = hsvColor(h, 0.05f, 0.93f),
        surfaceVariant          = hsvColor(h, 0.13f, 0.20f),
        onSurfaceVariant        = hsvColor(h, 0.16f, 0.74f),
        surfaceContainerLowest  = hsvColor(h, 0.10f, 0.06f),
        surfaceContainerLow     = hsvColor(h, 0.09f, 0.10f),
        surfaceContainer        = hsvColor(h, 0.08f, 0.14f),
        surfaceContainerHigh    = hsvColor(h, 0.07f, 0.18f),
        surfaceContainerHighest = hsvColor(h, 0.06f, 0.22f),
        background              = hsvColor(h, 0.07f, 0.10f),
        onBackground            = hsvColor(h, 0.05f, 0.93f),
        outline                 = hsvColor(h, 0.22f, 0.58f),
        outlineVariant          = hsvColor(h, 0.18f, 0.30f),
    )
}

@Composable
fun rememberThemePrefs(): State<ThemePrefs> {
    val context = LocalContext.current

    fun read(): ThemePrefs {
        val p = context.getSharedPreferences(settingsName, android.content.Context.MODE_PRIVATE)
        return ThemePrefs(
            mode = p.getString("themeMode", "system") ?: "system",
            customColor = p.getInt("themeColorValue", 0xFF43A047.toInt()).takeIf { it != -1 },
            fontFamily = p.getString("fontFamily", "default") ?: "default",
            outlineOnlyCards = p.getBoolean("outlineOnlyCards", false),
        )
    }

    val state = remember { mutableStateOf(read()) }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences(settingsName, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "themeMode" || key == "themeColorValue" || key == "fontFamily" || key == "outlineOnlyCards") state.value = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return state
}

@Composable
fun BeamTheme(prefs: ThemePrefs, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDark = when (prefs.mode) {
        "light" -> false
        "dark", "oled" -> true
        else -> isSystemInDarkTheme()
    }

    val baseScheme = when {
        prefs.customColor != null -> seedColorScheme(Color(prefs.customColor), useDark)
        Build.VERSION.SDK_INT >= 31 -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> seedColorScheme(Color(0xFF0080FF), useDark)
    }

    val colorScheme = if (prefs.mode == "oled") {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1C1C1C),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerLow = Color(0xFF0C0C0C),
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF1E1E1E),
            surfaceContainerHighest = Color(0xFF242424),
        )
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.surface.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalOutlineOnlyCards provides prefs.outlineOnlyCards) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typographyForFont(prefs.fontFamily),
            content = content,
        )
    }
}
