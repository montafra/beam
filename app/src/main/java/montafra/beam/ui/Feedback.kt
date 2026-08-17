package montafra.beam.ui

import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView

/** Swallows every haptic; used when haptic feedback is turned off. */
object NoOpHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

/** Whether tap sounds are enabled (`soundEnabled` preference). */
val LocalSoundEnabled = staticCompositionLocalOf { true }

/**
 * Haptics *without* the accompanying tap sound.
 *
 * `LocalHapticFeedback` is provided as a [TapFeedback], so every call site gets a click for free.
 * Continuous gestures — slider step ticks in particular — would fire one click per step, which is
 * grating, so they pull their haptics from here instead. Still gated by the haptics preference.
 */
val LocalSilentHaptics = staticCompositionLocalOf<HapticFeedback> { NoOpHapticFeedback }

/**
 * Decorates a [HapticFeedback] so that every discrete feedback event also plays the platform touch
 * sound. A null [haptics] or [view] disables that channel, which lets the two preferences
 * (`hapticsEnabled`, `soundEnabled`) be toggled independently.
 *
 * [View.playSoundEffect] routes through `AudioManager`, so it honours the system-wide
 * *Sound → Touch sounds* setting and needs no permission or bundled asset.
 */
class TapFeedback(
    private val haptics: HapticFeedback?,
    private val view: View?,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        haptics?.performHapticFeedback(hapticFeedbackType)
        view?.playSoundEffect(SoundEffectConstants.CLICK)
    }
}

/**
 * Plays the tap sound if it is enabled. For the rare call site that bypasses `LocalHapticFeedback`
 * — e.g. one going through [View.performHapticFeedback] directly — and so misses [TapFeedback].
 */
@Composable
fun rememberTapSound(): () -> Unit {
    val view = LocalView.current
    val enabled = LocalSoundEnabled.current
    return remember(view, enabled) {
        { if (enabled) view.playSoundEffect(SoundEffectConstants.CLICK) }
    }
}
