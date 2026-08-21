package montafra.beam

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import kotlin.math.roundToInt

/**
 * The selectable app fonts, keyed by the `fontFamily` preference.
 *
 * Every bundled file in res/font is a *variable* font, and their `wght` axes do not all
 * cover the same range — the bounds below are read from each file's fvar table. Space
 * Grotesk in particular bottoms out at 300 and its default instance *is* 300, so anything
 * that loads the file without pinning `wght` draws Light where Regular or Bold was asked
 * for. Nothing may assume a font can reach an arbitrary weight; go through [clampWeight].
 */
enum class BeamFont(
    val key: String,
    val label: String,
    @FontRes val resId: Int,
    val minWeight: Int,
    val maxWeight: Int,
) {
    Inter("inter", "Inter", R.font.inter, 100, 900),
    Gantari("gantari", "Gantari", R.font.gantari, 100, 900),
    DmSans("dm_sans", "DM Sans", R.font.dm_sans, 100, 1000),
    SpaceGrotesk("space_grotesk", "Space Grotesk", R.font.space_grotesk, 300, 700),
    JetBrainsMono("jetbrains_mono", "JetBrains Mono", R.font.jetbrains_mono, 100, 800),
    UbuntuSansMono("ubuntu_sans_mono", "Ubuntu Sans Mono", R.font.ubuntu_sans_mono, 400, 700);

    /** The nearest weight this font can actually draw. */
    fun clampWeight(weight: Int): Int = weight.coerceIn(minWeight, maxWeight)

    companion object {
        /** `null` for "default" and for any key we no longer ship — both mean the system font. */
        fun forKey(key: String?): BeamFont? = entries.firstOrNull { it.key == key }
    }
}

// The hero number's tap morph is authored as a sweep between these two weights.
const val heroRestWeight = 700
const val heroPressWeight = 200

/**
 * Rescales an authored [heroPressWeight]..[heroRestWeight] value onto the range this font
 * can actually draw. Plain clamping would flat-line the first 40% of the press on Space
 * Grotesk (floor 300) and 60% on Ubuntu Sans Mono (floor 400); rescaling keeps the morph
 * proportional on every font, just shallower where the axis is narrow. The release spring
 * overshoots past [heroRestWeight]; that is left intact on the fonts whose axis reaches
 * beyond it.
 */
fun BeamFont.mapHeroWeight(authored: Int): Int {
    val top = clampWeight(heroRestWeight)
    val bottom = clampWeight(heroPressWeight)
    val t = (authored - heroPressWeight).toFloat() / (heroRestWeight - heroPressWeight)
    return clampWeight((bottom + (top - bottom) * t).roundToInt())
}

/**
 * A [Typeface] for this font pinned to [weight], for the surfaces that draw text themselves
 * (the widget bitmaps and the notification icon) rather than through Compose. `null` means
 * the caller should keep the system font.
 *
 * `ResourcesCompat.getFont` hands back the file's *default* variable-font instance — Light
 * 300 for Space Grotesk — so the `wght` axis has to be applied on top of it. Going through
 * a Paint is the public API for that (API 26+; minSdk is 28): it derives a new Typeface from
 * the one already assigned, so the assignment has to come first.
 */
fun BeamFont.typeface(context: Context, weight: Int): Typeface? {
    val base = try {
        ResourcesCompat.getFont(context, resId)
    } catch (_: Exception) {
        null
    } ?: return null
    val paint = Paint()
    paint.typeface = base
    // Called as a method, not assigned as a property: it returns a boolean rather than void.
    paint.setFontVariationSettings("'wght' ${clampWeight(weight)}")
    return paint.typeface
}
