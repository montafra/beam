package montafra.beam.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import montafra.beam.R
import montafra.beam.ui.theme.hct.Hct
import montafra.beam.ui.theme.hct.TonalPalette
import montafra.beam.ui.theme.hct.sanitizeDegreesDouble

/**
 * How a seed colour is turned into a palette, chosen by the "Colour Style" theme setting.
 *
 * These are Material's own dynamic scheme variants: the seed only ever fixes a hue, and the style
 * decides how much chroma the accents carry, where the secondary and tertiary hues sit relative to
 * it, and how tinted the greys are. Same seed, seven very different rooms.
 *
 * Fidelity and Content are deliberately absent - both derive their tertiary from the seed's colour
 * temperature, which needs a whole further chunk of machinery for two styles that mostly reproduce
 * the seed as-is.
 */
enum class ColorStyle(val key: String, @StringRes val labelRes: Int) {
    TonalSpot("tonalSpot", R.string.colorStyleTonalSpot),
    Neutral("neutral", R.string.colorStyleNeutral),
    Vibrant("vibrant", R.string.colorStyleVibrant),
    FruitSalad("fruitSalad", R.string.colorStyleFruitSalad),
    Rainbow("rainbow", R.string.colorStyleRainbow),
    Expressive("expressive", R.string.colorStyleExpressive),
    Monochrome("monochrome", R.string.colorStyleMonochrome);

    companion object {
        /** Anything unset or no longer recognised means the Material default. */
        fun forKey(key: String?): ColorStyle = entries.firstOrNull { it.key == key } ?: TonalSpot
    }
}

/** The five ladders every colour role is picked off. */
private class StylePalettes(
    val primary: TonalPalette,
    val secondary: TonalPalette,
    val tertiary: TonalPalette,
    val neutral: TonalPalette,
    val neutralVariant: TonalPalette,
)

// Vibrant and Expressive don't shift their secondary and tertiary hues by a fixed amount: how far
// they swing depends on where the seed sits on the wheel, because equal steps in degrees are not
// equal steps in perceived hue. Each pair below is "seed hues up to here rotate by this much".
private val VibrantHues = doubleArrayOf(0.0, 41.0, 61.0, 101.0, 131.0, 181.0, 251.0, 301.0, 360.0)
private val VibrantSecondaryRotations = doubleArrayOf(18.0, 15.0, 10.0, 12.0, 15.0, 18.0, 15.0, 12.0, 12.0)
private val VibrantTertiaryRotations = doubleArrayOf(35.0, 30.0, 20.0, 25.0, 30.0, 35.0, 30.0, 25.0, 25.0)

private val ExpressiveHues = doubleArrayOf(0.0, 21.0, 51.0, 121.0, 151.0, 191.0, 271.0, 321.0, 360.0)
private val ExpressiveSecondaryRotations = doubleArrayOf(45.0, 95.0, 45.0, 20.0, 45.0, 90.0, 45.0, 45.0, 45.0)
private val ExpressiveTertiaryRotations = doubleArrayOf(120.0, 120.0, 20.0, 45.0, 20.0, 15.0, 20.0, 120.0, 120.0)

private fun rotatedHue(sourceHue: Double, hues: DoubleArray, rotations: DoubleArray): Double {
    for (i in 0..hues.size - 2) {
        if (hues[i] < sourceHue && sourceHue < hues[i + 1]) {
            return sanitizeDegreesDouble(sourceHue + rotations[i])
        }
    }
    // The seed landed exactly on a boundary; leaving it alone is what Material does too.
    return sourceHue
}

/**
 * How saturated the seed is, as a multiplier on every chroma a style asks for.
 *
 * Material's variants normally take only the seed's hue and impose their own chroma, which leaves
 * a colour picker with nothing to do but slide the hue around. Scaling by the seed's own chroma
 * gives the picker its saturation axis back: a washed-out seed mutes the whole palette, a vivid
 * one pushes a little past the canonical values.
 *
 * A saturated sRGB colour lands somewhere around chroma 50-110 depending on hue, so 48 is the
 * reference for "normal". The floor stops a near-grey seed collapsing the palette into something
 * with no hue left to read at all; the ceiling is reached at chroma 72, which is where the
 * picker's saturation slider tops out - past that the slider would move the swatch without
 * moving the theme.
 */
private fun chromaScale(seed: Hct): Double = (seed.chroma / 48.0).coerceIn(0.15, 1.5)

private fun palettesFor(style: ColorStyle, seed: Hct): StylePalettes {
    val h = seed.hue
    val k = chromaScale(seed)
    // Styles that are grey by definition stay exactly grey: 0.0 * k is still 0.0.
    fun palette(hue: Double, chroma: Double) = TonalPalette.fromHueAndChroma(hue, chroma * k)

    return when (style) {
        ColorStyle.TonalSpot -> StylePalettes(
            primary = palette(h, 36.0),
            secondary = palette(h, 16.0),
            tertiary = palette(h + 60.0, 24.0),
            neutral = palette(h, 6.0),
            neutralVariant = palette(h, 8.0),
        )

        ColorStyle.Neutral -> StylePalettes(
            primary = palette(h, 12.0),
            secondary = palette(h, 8.0),
            tertiary = palette(h, 16.0),
            neutral = palette(h, 2.0),
            neutralVariant = palette(h, 2.0),
        )

        // Chroma 200 is far outside sRGB on purpose: it asks each tone for as much colour as it
        // can physically give, and the gamut search clamps it per tone.
        ColorStyle.Vibrant -> StylePalettes(
            primary = palette(h, 200.0),
            secondary = palette(rotatedHue(h, VibrantHues, VibrantSecondaryRotations), 24.0),
            tertiary = palette(rotatedHue(h, VibrantHues, VibrantTertiaryRotations), 32.0),
            neutral = palette(h, 10.0),
            neutralVariant = palette(h, 12.0),
        )

        // The accent deliberately lands nowhere near the seed - that near-complementary jump is
        // the whole character of this one.
        ColorStyle.Expressive -> StylePalettes(
            primary = palette(h + 240.0, 40.0),
            secondary = palette(rotatedHue(h, ExpressiveHues, ExpressiveSecondaryRotations), 24.0),
            tertiary = palette(rotatedHue(h, ExpressiveHues, ExpressiveTertiaryRotations), 32.0),
            neutral = palette(h + 15.0, 8.0),
            neutralVariant = palette(h + 15.0, 12.0),
        )

        // Saturated accents over completely untinted greys.
        ColorStyle.Rainbow -> StylePalettes(
            primary = palette(h, 48.0),
            secondary = palette(h, 16.0),
            tertiary = palette(h + 60.0, 24.0),
            neutral = palette(h, 0.0),
            neutralVariant = palette(h, 0.0),
        )

        ColorStyle.FruitSalad -> StylePalettes(
            primary = palette(h - 50.0, 48.0),
            secondary = palette(h - 50.0, 36.0),
            tertiary = palette(h, 36.0),
            neutral = palette(h, 10.0),
            neutralVariant = palette(h, 16.0),
        )

        ColorStyle.Monochrome -> StylePalettes(
            primary = palette(h, 0.0),
            secondary = palette(h, 0.0),
            tertiary = palette(h, 0.0),
            neutral = palette(h, 0.0),
            neutralVariant = palette(h, 0.0),
        )
    }
}

private fun TonalPalette.color(tone: Int): Color = Color(this.tone(tone))

/**
 * Every Material colour role, read off [style]'s palettes at the tones Material specifies for
 * standard contrast.
 *
 * Unlike the HSV table this replaced, nothing is left to the library's baseline - `tertiary`,
 * `error`, the `inverse*` roles and `surfaceTint` all track the seed now instead of quietly
 * staying the stock purple.
 */
internal fun dynamicColorScheme(seedArgb: Int, style: ColorStyle, isDark: Boolean): ColorScheme {
    val p = palettesFor(style, Hct.fromInt(seedArgb))
    // Error keeps its own fixed red across every style, as Material intends - a warning that
    // drifts with the theme stops reading as a warning.
    val error = TonalPalette.fromHueAndChroma(25.0, 84.0)

    return if (isDark) darkColorScheme(
        primary                 = p.primary.color(80),
        onPrimary               = p.primary.color(20),
        primaryContainer        = p.primary.color(30),
        onPrimaryContainer      = p.primary.color(90),
        inversePrimary          = p.primary.color(40),
        secondary               = p.secondary.color(80),
        onSecondary             = p.secondary.color(20),
        secondaryContainer      = p.secondary.color(30),
        onSecondaryContainer    = p.secondary.color(90),
        tertiary                = p.tertiary.color(80),
        onTertiary              = p.tertiary.color(20),
        tertiaryContainer       = p.tertiary.color(30),
        onTertiaryContainer     = p.tertiary.color(90),
        background              = p.neutral.color(6),
        onBackground            = p.neutral.color(90),
        surface                 = p.neutral.color(6),
        onSurface               = p.neutral.color(90),
        surfaceVariant          = p.neutralVariant.color(30),
        onSurfaceVariant        = p.neutralVariant.color(80),
        surfaceTint             = p.primary.color(80),
        inverseSurface          = p.neutral.color(90),
        inverseOnSurface        = p.neutral.color(20),
        error                   = error.color(80),
        onError                 = error.color(20),
        errorContainer          = error.color(30),
        onErrorContainer        = error.color(90),
        outline                 = p.neutralVariant.color(60),
        outlineVariant          = p.neutralVariant.color(30),
        scrim                   = p.neutral.color(0),
        surfaceBright           = p.neutral.color(24),
        surfaceDim              = p.neutral.color(6),
        surfaceContainerLowest  = p.neutral.color(4),
        surfaceContainerLow     = p.neutral.color(10),
        surfaceContainer        = p.neutral.color(12),
        surfaceContainerHigh    = p.neutral.color(17),
        surfaceContainerHighest = p.neutral.color(22),
    ) else lightColorScheme(
        primary                 = p.primary.color(40),
        onPrimary               = p.primary.color(100),
        primaryContainer        = p.primary.color(90),
        onPrimaryContainer      = p.primary.color(10),
        inversePrimary          = p.primary.color(80),
        secondary               = p.secondary.color(40),
        onSecondary             = p.secondary.color(100),
        secondaryContainer      = p.secondary.color(90),
        onSecondaryContainer    = p.secondary.color(10),
        tertiary                = p.tertiary.color(40),
        onTertiary              = p.tertiary.color(100),
        tertiaryContainer       = p.tertiary.color(90),
        onTertiaryContainer     = p.tertiary.color(10),
        background              = p.neutral.color(98),
        onBackground            = p.neutral.color(10),
        surface                 = p.neutral.color(98),
        onSurface               = p.neutral.color(10),
        surfaceVariant          = p.neutralVariant.color(90),
        onSurfaceVariant        = p.neutralVariant.color(30),
        surfaceTint             = p.primary.color(40),
        inverseSurface          = p.neutral.color(20),
        inverseOnSurface        = p.neutral.color(95),
        error                   = error.color(40),
        onError                 = error.color(100),
        errorContainer          = error.color(90),
        onErrorContainer        = error.color(10),
        outline                 = p.neutralVariant.color(50),
        outlineVariant          = p.neutralVariant.color(80),
        scrim                   = p.neutral.color(0),
        surfaceBright           = p.neutral.color(98),
        surfaceDim              = p.neutral.color(87),
        surfaceContainerLowest  = p.neutral.color(100),
        surfaceContainerLow     = p.neutral.color(96),
        surfaceContainer        = p.neutral.color(94),
        surfaceContainerHigh    = p.neutral.color(92),
        surfaceContainerHighest = p.neutral.color(90),
    )
}

/** The handful of colours a style preview shows, without paying for a whole scheme to get them. */
internal class StylePreview(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
)

/**
 * Four representative colours for [style] at [seedArgb].
 *
 * Deliberately not [dynamicColorScheme]: a scheme resolves some fifty tones, each one a gamut
 * search, which is far too much to redo while a finger is dragging across a colour field. Four
 * tones is cheap enough to stay live.
 */
internal fun stylePreview(seedArgb: Int, style: ColorStyle, isDark: Boolean): StylePreview {
    val p = palettesFor(style, Hct.fromInt(seedArgb))
    return if (isDark) {
        StylePreview(
            primary = p.primary.color(80),
            secondary = p.secondary.color(80),
            tertiary = p.tertiary.color(80),
            surface = p.neutral.color(22),
        )
    } else {
        StylePreview(
            primary = p.primary.color(40),
            secondary = p.secondary.color(40),
            tertiary = p.tertiary.color(40),
            surface = p.neutral.color(90),
        )
    }
}
