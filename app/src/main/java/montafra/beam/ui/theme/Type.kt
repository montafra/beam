package montafra.beam.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import montafra.beam.R

val WattzTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 68.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
)

private fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@OptIn(ExperimentalTextApi::class)
fun variableFamily(resId: Int): FontFamily = FontFamily(
    Font(resId, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(resId, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(resId, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

fun fontResFor(key: String): Int? = when (key) {
    "inter" -> R.font.inter
    "dm_sans" -> R.font.dm_sans
    "space_grotesk" -> R.font.space_grotesk
    "jetbrains_mono" -> R.font.jetbrains_mono
    "gantari" -> R.font.gantari
    "ubuntu_sans_mono" -> R.font.ubuntu_sans_mono
    else -> null
}

fun fontFamilyFor(key: String): FontFamily? = fontResFor(key)?.let { variableFamily(it) }

// Single-Font family at an arbitrary (animatable) weight, for the hero number's
// tap effect. The bundled fonts are variable (wght axis) so this morphs smoothly;
// "default" falls back to the system font, whose weight animates best-effort.
@OptIn(ExperimentalTextApi::class)
fun heroNumberFontFamily(key: String, weight: Int): FontFamily {
    val w = weight.coerceIn(1, 1000)
    return fontResFor(key)?.let {
        FontFamily(
            Font(it, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))),
        )
    } ?: FontFamily.Default
}

fun typographyForFont(key: String): Typography =
    fontFamilyFor(key)?.let { WattzTypography.withFontFamily(it) } ?: WattzTypography
