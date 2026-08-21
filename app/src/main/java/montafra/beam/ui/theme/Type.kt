package montafra.beam.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import montafra.beam.BeamFont
import montafra.beam.mapHeroWeight

private val m3 = Typography()

// Only the two roles Beam restyles; everything else keeps the Material3 defaults, which is
// why these copy() the baseline rather than building a TextStyle from scratch — a fresh
// TextStyle would drop the baseline letterSpacing, lineHeightStyle and explicit fontWeight.
val WattzTypography = m3.copy(
    displayLarge = m3.displayLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 68.sp,
    ),
    bodyLarge = m3.bodyLarge.copy(
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

private val weightLadder = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)

/**
 * A family covering every weight step the font can actually draw, each entry pinned to the
 * matching `wght` axis value.
 *
 * The bundled fonts are single variable files, so a family has to spell out its weights: an
 * entry Compose can't match falls back to the file's default instance, which is Light 300
 * for Space Grotesk and leaves bold text looking thin. Steps outside the font's axis are
 * left out rather than declared and clamped, so Compose resolves e.g. a W300 request on
 * Ubuntu Sans Mono up to its real 400 instead of matching an entry that can't honour it.
 * Entries are loaded lazily, so listing nine costs nothing until a weight is used.
 */
@OptIn(ExperimentalTextApi::class)
fun BeamFont.fontFamily(): FontFamily = FontFamily(
    weightLadder.filter { it in minWeight..maxWeight }.map { w ->
        Font(
            resId,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

// Six entries at most, and MaterialTheme publishes typography through a static
// CompositionLocal — handing back the same instance keeps a theme recomposition from
// rebuilding a family plus fifteen TextStyle copies every time.
private val familyCache = HashMap<BeamFont, FontFamily>()
private val typographyCache = HashMap<BeamFont, Typography>()

private fun familyFor(font: BeamFont): FontFamily =
    synchronized(familyCache) { familyCache.getOrPut(font) { font.fontFamily() } }

fun fontFamilyFor(key: String): FontFamily? = BeamFont.forKey(key)?.let(::familyFor)

// Quantised so a press produces ~20 distinct typefaces instead of ~500. Compose caches
// typefaces in a small LRU, and an unquantised sweep thrashes it badly enough to rebuild
// faces mid-animation; 25-point steps are indistinguishable by eye.
private const val heroWeightStep = 25

// Bounded by the quantised ladder — at most one entry per step inside each font's axis.
// Caching by value lets a sweep reuse instances across frames instead of allocating a
// family, and re-resolving a typeface, on every one.
private val heroFamilyCache = HashMap<Int, FontFamily>()

/**
 * The weight to draw the hero number at for a raw animation value, remapped onto the
 * selected font's axis. Callers must feed the result to both the family and the TextStyle
 * so the requested weight and the declared entry always agree.
 */
fun heroWeightFor(key: String, authored: Int): Int {
    val stepped = (authored.toFloat() / heroWeightStep).roundToInt() * heroWeightStep
    return BeamFont.forKey(key)?.mapHeroWeight(stepped)
        ?: stepped.coerceIn(1, 1000)
}

// Single-Font family at an arbitrary (animatable) weight, for the hero number's tap effect.
// The bundled fonts are variable (wght axis) so this morphs smoothly; "default" falls back
// to the system font, whose weight animates best-effort.
@OptIn(ExperimentalTextApi::class)
fun heroNumberFontFamily(key: String, weight: Int): FontFamily {
    val font = BeamFont.forKey(key) ?: return FontFamily.Default
    val w = font.clampWeight(weight)
    return synchronized(heroFamilyCache) {
        heroFamilyCache.getOrPut((font.ordinal shl 16) or w) {
            FontFamily(
                Font(
                    font.resId,
                    weight = FontWeight(w),
                    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
                ),
            )
        }
    }
}

fun typographyForFont(key: String): Typography {
    val font = BeamFont.forKey(key) ?: return WattzTypography
    return synchronized(typographyCache) {
        typographyCache.getOrPut(font) { WattzTypography.withFontFamily(familyFor(font)) }
    }
}
